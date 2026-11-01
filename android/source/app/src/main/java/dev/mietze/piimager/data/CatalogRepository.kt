package dev.mietze.piimager.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CatalogRepository(private val context: Context) {
    companion object {
        const val DEFAULT_REPO =
            "https://downloads.raspberrypi.com/os_list_imagingutility_v4.json"
    }

    @Volatile var status: String = "No metadata loaded"
        private set

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(false)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun load(repoUrl: String = DEFAULT_REPO, force: Boolean = false): OsCatalog =
        withContext(Dispatchers.IO) {
            val cache = File(context.cacheDir, "os_catalog_v4.json")
            val cached = if (repoUrl == DEFAULT_REPO) runCatching {
                if (!cache.isFile || cache.length() > 2_097_152L) null else parse(JSONObject(cache.readText()))
            }.getOrNull() else null
            if (!force && cached != null) {
                status = "Cached catalogue · saved ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(cache.lastModified()))} · Refresh metadata to check for updates."
                return@withContext cached
            }
            try {
                val json = fetch(repoUrl)
                val result = parse(JSONObject(json))
                var saved = true
                if (repoUrl == DEFAULT_REPO) saved = runCatching {
                    synchronized(CatalogRepository::class.java) {
                        val atomic = android.util.AtomicFile(cache)
                        val output = atomic.startWrite()
                        try {
                            output.write(json.toByteArray(Charsets.UTF_8))
                            atomic.finishWrite(output)
                        } catch (e: Exception) {
                            atomic.failWrite(output)
                            throw e
                        }
                    }
                }.isSuccess
                status = if (saved) "Fresh publisher metadata loaded. No OS image downloaded." else "Fresh metadata loaded; cache could not be saved. No OS image downloaded."
                result
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (cached == null) throw e
                status = "Refresh unavailable (${e.message ?: "network error"}). Showing previously cached metadata; it may be outdated."
                cached
            }
        }

    /** Clears only this app's catalogue JSON cache, never sources or image files. */
    fun clearCache(): Boolean = runCatching {
        synchronized(CatalogRepository::class.java) {
            val cache = File(context.cacheDir, "os_catalog_v4.json")
            android.util.AtomicFile(cache).delete()
            !cache.exists()
        }
    }.getOrDefault(false)

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RaspberryManager/${dev.mietze.piimager.ReleaseInfo.VERSION}")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Repository HTTP ${response.code}" }
            val body = response.body ?: error("Repository response was empty")
            require(body.contentLength() <= 2_097_152L) { "Repository metadata exceeds 2 MiB" }
            val out = java.io.ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= 2_097_152) { "Repository metadata exceeds 2 MiB" }
                    out.write(buffer, 0, n)
                }
            }
            return out.toString("UTF-8")
        }
    }

    private fun parse(root: JSONObject): OsCatalog {
        require(root.optJSONArray("os_list")?.length()?.let { it > 0 } == true) { "The catalogue has no operating-system entries" }
        val imager = root.optJSONObject("imager") ?: JSONObject()
        val devices = parseDevices(imager.optJSONArray("devices") ?: JSONArray())
        val os = parseItems(root.optJSONArray("os_list") ?: JSONArray())
        return OsCatalog(devices, os)
    }

    private fun parseDevices(array: JSONArray): List<PiDevice> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            add(
                PiDevice(
                    name = o.optString("name"),
                    description = o.optString("description"),
                    icon = o.optString("icon").takeIf { it.isNotBlank() },
                    tags = stringList(o.optJSONArray("tags"))
                )
            )
        }
    }

    private fun parseItems(array: JSONArray): List<OsItem> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val children = parseItems(o.optJSONArray("subitems") ?: JSONArray())
            add(
                OsItem(
                    name = o.optString("name"),
                    description = o.optString("description"),
                    icon = normalizeIcon(o.optString("icon")),
                    url = o.optString("url").takeIf { it.isNotBlank() },
                    extractSize = o.optLongOrNull("extract_size"),
                    extractSha256 = o.optString("extract_sha256").takeIf { it.isNotBlank() },
                    downloadSize = o.optLongOrNull("image_download_size"),
                    downloadSha256 = o.optString("image_download_sha256").takeIf { it.isNotBlank() },
                    releaseDate = o.optString("release_date").takeIf { it.isNotBlank() },
                    initFormat = o.optString("init_format").takeIf { it.isNotBlank() },
                    devices = stringList(o.optJSONArray("devices")),
                    subitems = children
                )
            )
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0L } else null

    private fun stringList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return buildList {
            for (i in 0 until a.length()) {
                a.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun normalizeIcon(value: String): String? {
        if (value.isBlank()) return null
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        return "https://downloads.raspberrypi.com/imager/${value.removePrefix("/")}"
    }
}

fun filterOsForDevice(items: List<OsItem>, tags: List<String>): List<OsItem> {
    if (tags.isEmpty()) return items
    fun filter(item: OsItem): OsItem? {
        val children = item.subitems.mapNotNull(::filter)
        if (children.isNotEmpty()) return item.copy(subitems = children)
        val matches = item.devices.isEmpty() || item.devices.any(tags::contains)
        return item.takeIf { matches && !item.url.isNullOrBlank() }
    }
    return items.mapNotNull(::filter)
}

fun flattenOs(items: List<OsItem>): List<OsItem> = buildList {
    fun walk(list: List<OsItem>) {
        list.forEach {
            if (!it.url.isNullOrBlank()) add(it)
            if (it.subitems.isNotEmpty()) walk(it.subitems)
        }
    }
    walk(items)
}
