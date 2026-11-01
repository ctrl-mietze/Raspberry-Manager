package dev.mietze.piimager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mietze.piimager.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Bundled metadata only. No ROM/image is embedded, prefetched or downloaded on activation. */
data class LibraryEntry(
 val id: String, val name: String, val category: String, val description: String,
 val website: String, val match: String, val note: String = "Model support depends on the selected image.",
 val imageUrl: String? = null, val imageSha256: String? = null
)

object LibraryDefaults {
 val entries = listOf(
  LibraryEntry("ubuntu", "Ubuntu", "Desktop & Server", "Desktop and server editions for Raspberry Pi.", "https://ubuntu.com/download/raspberry-pi", "Ubuntu"),
  LibraryEntry("dietpi", "DietPi", "Lightweight", "A compact Linux base with optional software installation.", "https://dietpi.com/", "DietPi"),
  LibraryEntry("libreelec", "LibreELEC", "Media", "A focused Kodi-based media-centre operating system.", "https://libreelec.tv/downloads/raspberry/", "LibreELEC"),
  LibraryEntry("osmc", "OSMC", "Media", "A media-centre distribution with Raspberry Pi images.", "https://osmc.tv/download/", "OSMC"),
  LibraryEntry("volumio", "Volumio", "Media", "A music-player operating system for your Raspberry Pi.", "https://volumio.com/", "Volumio"),
  LibraryEntry("retropie", "RetroPie", "Gaming", "A retro-gaming setup. Game files are not included.", "https://retropie.org.uk/download/", "RetroPie", "No game ROMs included. Check the exact Raspberry model."),
  LibraryEntry("recalbox", "Recalbox", "Gaming", "A dedicated retro-gaming system for supported boards.", "https://www.recalbox.com/download/", "Recalbox", "Game ROMs are not downloaded by this library."),
  LibraryEntry("homeassistant", "Home Assistant OS", "Smart home", "An operating system dedicated to Home Assistant.", "https://www.home-assistant.io/installation/raspberrypi/", "Home Assistant"),
  LibraryEntry("kali", "Kali Linux", "Research", "ARM images for authorised security testing and research.", "https://www.kali.org/get-kali/#kali-arm", "Kali Linux"),
  LibraryEntry("emteria", "Android by emteria", "Android", "Android images distributed through the Raspberry Pi catalogue.", "https://emteria.com/", "Android by emteria", "Vendor licensing may apply. Follow the image's SD-card and board requirements.")
 )
 val ids: Set<String> get() = entries.map { it.id }.toSet()
}

data class LibraryState(
 val enabled: Boolean = false,
 val enabledIds: Set<String> = emptySet(),
 val entries: List<LibraryEntry> = LibraryDefaults.entries,
 val selected: LibraryEntry? = null,
 val variants: List<OsItem> = emptyList(),
 val loading: Boolean = false,
 val error: String? = null,
 val catalogueLoaded: Boolean = false,
 val metadataStatus: String = "No catalogue loaded in this session. Image downloads: manual only.",
 val favorites: Set<String> = emptySet(),
 val recentIds: List<String> = emptyList()
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
 private val prefs = app.getSharedPreferences("pi_manager_library", 0)
 private val repository = CatalogRepository(app)
 private var catalog: OsCatalog? = null
 private var request: Job? = null
 private val _ui = MutableStateFlow(LibraryState(
  enabled = prefs.getBoolean("enabled", false),
  entries = LibraryDefaults.entries + loadCustomEntries(),
  enabledIds = prefs.getStringSet("enabled_ids", emptySet())?.toSet().orEmpty(),
  favorites = prefs.getStringSet("favorites", emptySet())?.toSet().orEmpty(),
  recentIds = runCatching {
   val array = JSONArray(prefs.getString("recent_ids", "[]"))
   (0 until array.length()).map { array.getString(it) }.take(6)
  }.getOrDefault(emptyList())
 ))
 val ui = _ui.asStateFlow()

 /** This method does no network or filesystem-image work. */
 fun activate(enabled: Boolean) {
  val first = enabled && !prefs.contains("activated_once")
  val ids = LibraryActivation.enabledSources(enabled, !prefs.contains("activated_once"), _ui.value.enabledIds)
  prefs.edit().putBoolean("enabled", enabled).putStringSet("enabled_ids", ids).apply {
   if (first) putBoolean("activated_once", true)
  }.apply()
  _ui.update { it.copy(enabled = enabled, enabledIds = ids) }
  if (!enabled) closeEntry()
 }

 private fun loadCustomEntries(): List<LibraryEntry> = runCatching {
  LibraryBackup.parseSources(JSONArray(prefs.getString("custom_sources", "[]")))
 }.getOrDefault(emptyList())

 private fun persistLibrary(entries: List<LibraryEntry>, enabledIds: Set<String>, favorites: Set<String>, recent: List<String>) {
  val custom = entries.filter { it.imageUrl != null }
  require(custom.size <= LibraryBackup.MAX_CUSTOM) { "At most 100 custom image links are supported" }
  val known = entries.map { it.id }.toSet()
  val enabled = enabledIds.intersect(known)
  val stars = favorites.intersect(known)
  val history = recent.filter { it in known }.distinct().take(6)
  val array = JSONArray().apply { custom.forEach { put(LibraryBackup.encodeEntry(it)) } }
  prefs.edit().putString("custom_sources", array.toString()).putStringSet("enabled_ids", enabled)
   .putStringSet("favorites", stars).putString("recent_ids", JSONArray(history).toString()).apply()
  _ui.update { old ->
   val selected = old.selected?.takeIf { it.id in enabled }
   old.copy(entries = entries, enabledIds = enabled, favorites = stars, recentIds = history,
    selected = selected, variants = if (selected == null) emptyList() else old.variants)
  }
 }

 fun setSourceEnabled(id: String, enabled: Boolean) {
  val state = _ui.value
  if (state.entries.none { it.id == id }) return
  val ids = if (enabled) state.enabledIds + id else state.enabledIds - id
  persistLibrary(state.entries, ids, state.favorites, state.recentIds)
  if (!enabled && state.selected?.id == id) closeEntry()
 }

 fun addCustom(name: String, url: String, sha256: String) {
  val state = _ui.value
  require(state.enabled) { "Activate the custom library first" }
  val entry = LibraryBackup.customEntry(name, url, sha256)
  val next = state.entries.filterNot { it.id == entry.id } + entry
  persistLibrary(next, state.enabledIds + entry.id, state.favorites, state.recentIds)
 }

 fun removeCustom(id: String) {
  val state = _ui.value
  require(state.entries.any { it.id == id && it.imageUrl != null }) { "Only custom image links can be removed" }
  persistLibrary(state.entries.filterNot { it.id == id }, state.enabledIds - id, state.favorites - id, state.recentIds.filterNot { it == id })
  if (state.selected?.id == id) closeEntry()
 }

 fun exportMetadata(): String = LibraryBackup.encode(_ui.value)

 fun clearMetadataCache() {
  request?.cancel()
  request = viewModelScope.launch {
   val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repository.clearCache() }
   catalog = null
   _ui.update { it.copy(variants = emptyList(), loading = false, catalogueLoaded = false,
    metadataStatus = if (result) "Catalogue cache cleared. Sources and images unchanged." else "Catalogue cache could not be cleared; saved sources are unchanged.") }
  }
 }

 fun mergeImport(plan: LibraryImportPlan) {
  val state = _ui.value
  val incoming = plan.entries.associateBy { it.id }
  val merged = state.entries.filterNot { it.id in incoming } + incoming.values
  persistLibrary(merged, state.enabledIds + plan.enabledIds, state.favorites + plan.favorites, state.recentIds)
 }

 fun toggleFavorite(id: String) {
  if (_ui.value.entries.none { it.id == id }) return
  val current = _ui.value.favorites
  val next = if (id in current) current - id else current + id
  prefs.edit().putStringSet("favorites", next).apply()
  _ui.update { it.copy(favorites = next) }
 }

 fun selectEntry(entry: LibraryEntry) {
  if (!_ui.value.enabled || entry.id !in _ui.value.enabledIds) return
  request?.cancel()
  val recent = (listOf(entry.id) + _ui.value.recentIds.filterNot { it == entry.id }).take(6)
  prefs.edit().putString("recent_ids", JSONArray(recent).toString()).apply()
  _ui.update { it.copy(selected = entry, variants = emptyList(), error = null, loading = false, recentIds = recent) }
 }

 fun closeEntry() {
  request?.cancel()
  _ui.update { it.copy(selected = null, variants = emptyList(), loading = false, error = null) }
 }

 /** Triggered only by the user's 'Load image variants' or 'Refresh metadata' action. */
 fun loadVariants(force: Boolean = false) {
  val entry = _ui.value.selected ?: return
  if (!_ui.value.enabled) return
  request?.cancel()
  if (entry.imageUrl != null) {
   val item = OsItem(entry.name, entry.description, null, entry.imageUrl, null, null,
    null, entry.imageSha256, null, "none", emptyList(), emptyList())
   _ui.update { it.copy(variants = listOf(item), loading = false, error = null, metadataStatus = "Saved custom link. No network request or image download performed.") }
   return
  }
  _ui.update { it.copy(loading = true, error = null) }
  request = viewModelScope.launch {
   try {
    val current = if (!force && catalog != null) catalog!! else repository.load(force = force).also { catalog = it }
    val result = LibraryWire.matchingVariants(current.osList, entry.match)
    _ui.update { old ->
     if (old.selected?.id != entry.id) old else old.copy(
      variants = result, loading = false, catalogueLoaded = true, metadataStatus = repository.status,
      error = if (result.isEmpty()) "No supported disk-image variants are listed in the current catalogue. Open the vendor page or try refreshing metadata." else null
     )
    }
   } catch (e: CancellationException) { throw e }
   catch (e: Exception) {
    _ui.update { old -> if (old.selected?.id != entry.id) old else old.copy(loading = false, error = e.message ?: "Metadata unavailable") }
   }
  }
 }
}

object LibraryWire {
 const val EXTRA_IMAGE = "dev.mietze.raspberrymanager.LIBRARY_IMAGE"
 fun matchingVariants(items: List<OsItem>, term: String): List<OsItem> {
  val out = mutableListOf<OsItem>()
  fun walk(list: List<OsItem>, matched: Boolean) {
   list.forEach { item ->
    val hit = matched || item.name.contains(term, ignoreCase = true)
    if (hit && !item.url.isNullOrBlank() && supportedImage(item.url)) out += item
    if (item.subitems.isNotEmpty()) walk(item.subitems, hit)
   }
  }
  walk(items, false)
  return out.distinctBy { it.url }.take(200)
 }
 fun supportedImage(url: String): Boolean = runCatching {
  val path = URI(url).path.lowercase()
  listOf(".img", ".raw", ".img.xz", ".img.gz", ".img.zip", ".zip", ".xz", ".gz").any(path::endsWith)
 }.getOrDefault(false)
 fun validUrl(url: String, httpsOnly: Boolean = false): Boolean = runCatching {
  val u = URI(url)
  url.length in 8..4096 && url.none { it.isISOControl() } &&
   (u.scheme == "https" || (!httpsOnly && u.scheme == "http")) &&
   !u.host.isNullOrBlank() && u.rawUserInfo == null && u.fragment == null
 }.getOrDefault(false)
 fun encode(item: OsItem): String = JSONObject().apply {
  put("name", item.name); put("description", item.description); put("url", item.url)
  put("extract_size", item.extractSize); put("extract_sha256", item.extractSha256)
  put("image_download_size", item.downloadSize); put("image_download_sha256", item.downloadSha256)
  put("release_date", item.releaseDate); put("init_format", item.initFormat)
  put("devices", JSONArray(item.devices))
 }.toString()
 fun decode(text: String): OsItem {
  require(text.length <= 65536) { "Image metadata exceeds the allowed size" }
  val o = JSONObject(text)
  val url = o.getString("url")
  require(validUrl(url) && supportedImage(url)) { "Only HTTP(S) disk-image URLs are supported" }
  fun string(key: String) = if (o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() }
  fun size(key: String) = if (o.isNull(key)) null else o.optLong(key).takeIf { it > 0 }
  fun hash(key: String): String? = string(key)?.also { require(it.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid SHA-256 metadata" } }
  val tags = o.optJSONArray("devices") ?: JSONArray()
  return OsItem(o.getString("name").take(200), o.optString("description").take(2000), null, url,
   size("extract_size"), hash("extract_sha256"), size("image_download_size"), hash("image_download_sha256"),
   string("release_date"), string("init_format"), (0 until tags.length()).map { tags.getString(it) }.take(100), emptyList())
 }
}
