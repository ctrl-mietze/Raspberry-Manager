package dev.mietze.piimager

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.UUID

/** Export/import only contains OS source metadata: never device credentials or OS image bytes. */
data class LibraryImportPlan(val entries: List<LibraryEntry>, val enabledIds: Set<String>, val favorites: Set<String>)

object LibraryBackup {
 const val MAX_BYTES = 262144
 const val MAX_CUSTOM = 100
 fun customEntry(name: String, imageUrl: String, sha256: String): LibraryEntry {
  val title = name.trim()
  val url = imageUrl.trim()
  val hash = sha256.trim().lowercase()
  require(title.length in 1..80 && title.none { it.isISOControl() }) { "Enter a name between 1 and 80 characters" }
  require(LibraryWire.validUrl(url, httpsOnly = true) && LibraryWire.supportedImage(url)) {
   "Use a direct HTTPS image URL ending in .img, .raw, .xz, .gz or .zip, without embedded credentials"
  }
  require(hash.isBlank() || hash.matches(Regex("[a-f0-9]{64}"))) { "SHA-256 must be empty or exactly 64 hexadecimal characters" }
  val uri = URI(url)
  val site = URI(uri.scheme, null, uri.host, uri.port, "/", null, null).toString()
  val id = "custom-" + UUID.nameUUIDFromBytes(url.toByteArray(Charsets.UTF_8))
  return LibraryEntry(id, title, "Custom", "Your own image link. Download requires explicit confirmation in the Imager.", site, "",
   "User-supplied image. Verify the Raspberry model and disk-image format yourself. No model compatibility is assumed.", url, hash.takeIf { it.isNotBlank() })
 }
 fun encodeEntry(entry: LibraryEntry): JSONObject = JSONObject().apply {
  require(entry.imageUrl != null) { "Only custom image links can be exported as custom sources" }
  put("id", entry.id); put("name", entry.name); put("image_url", entry.imageUrl)
  put("download_sha256", entry.imageSha256 ?: "")
 }
 fun parseSources(array: JSONArray): List<LibraryEntry> {
  require(array.length() <= MAX_CUSTOM) { "A library can contain at most $MAX_CUSTOM custom links" }
  return (0 until array.length()).map { index ->
   val item = array.getJSONObject(index)
   customEntry(item.getString("name"), item.getString("image_url"), item.optString("download_sha256", ""))
  }.distinctBy { it.id }
 }
 fun encode(state: LibraryState): String = JSONObject().apply {
  put("format", "raspberry-manager-library"); put("schema", 1)
  put("custom_sources", JSONArray().apply { state.entries.filter { it.imageUrl != null }.forEach { put(encodeEntry(it)) } })
  put("enabled_ids", JSONArray(state.enabledIds.toList().sorted()))
  put("favorites", JSONArray(state.favorites.toList().sorted()))
 }.toString(2)
 fun parse(text: String): LibraryImportPlan {
  require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup metadata exceeds 256 KiB" }
  val root = JSONObject(text)
  require(root.optString("format") == "raspberry-manager-library" && root.optInt("schema") == 1) { "Not a supported Raspberry Manager library backup" }
  val entries = parseSources(root.getJSONArray("custom_sources"))
  val valid = LibraryDefaults.ids + entries.map { it.id }
  fun ids(key: String): Set<String> {
   val array = root.optJSONArray(key) ?: JSONArray()
   require(array.length() <= MAX_CUSTOM + LibraryDefaults.ids.size) { "Too many source IDs" }
   return (0 until array.length()).map { array.getString(it) }.filter { it in valid }.toSet()
  }
  return LibraryImportPlan(entries, ids("enabled_ids"), ids("favorites"))
 }
 fun readLimited(input: InputStream): String {
  val out = ByteArrayOutputStream()
  val buffer = ByteArray(8192)
  while (true) {
   val n = input.read(buffer)
   if (n < 0) break
   require(out.size() + n <= MAX_BYTES) { "Backup metadata exceeds 256 KiB" }
   out.write(buffer, 0, n)
  }
  return out.toString("UTF-8")
 }
}
