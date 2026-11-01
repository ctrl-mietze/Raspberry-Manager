package dev.mietze.piimager

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Only public, fingerprinted JavaScript/stylesheets at the provider's two official origins.
 * No HTML, API, session, ID, query-bearing URL or arbitrary host is proxied. */
internal object ConnectAssetPolicy {
    const val ORIGIN = "https://connect.raspberrypi.com"
    const val MAX_BYTES = 8 * 1024 * 1024
    private val scriptPath = Regex("/assets/(?:[A-Za-z0-9_-]+/){0,2}[A-Za-z0-9_.-]+-[a-fA-F0-9]{8,64}\\.(?:js|css)")
    fun target(url: String, method: String = "GET", mainFrame: Boolean = false): String? {
        if (method != "GET" || mainFrame || url.length > 2048 || url != url.trim()) return null
        val u = runCatching { URI(url) }.getOrNull() ?: return null
        if (u.scheme != "https" || !u.host.orEmpty().equals("connect-assets.raspberrypi.com", true) ||
            u.rawUserInfo != null || u.port !in setOf(-1, 443) || u.rawQuery != null || u.rawFragment != null ||
            !scriptPath.matches(u.rawPath.orEmpty())) return null
        return ORIGIN + u.rawPath
    }
    fun validMime(value: String, stylesheet: Boolean = false): Boolean = if (stylesheet) value.substringBefore(';').trim().equals("text/css", true) else value.substringBefore(';').trim().lowercase() in
        setOf("text/javascript", "application/javascript", "application/ecmascript", "text/ecmascript")
    fun validCors(value: String): Boolean = value.trim() in setOf(ORIGIN, "*")
    fun validBody(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return false
        val beginning = bytes.take(256).toByteArray().toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase()
        return !beginning.startsWith("<")
    }
}

internal data class ConnectPublicScript(val bytes: ByteArray, val mime: String, val cors: String)

/** The source responds anonymously at the identical immutable /assets filename.
 * Original provider code is executed unchanged by WebView. No cookie extraction,
 * client ID changes, redirect following, TLS exceptions or arbitrary JS patches. */
internal class ConnectAssetSource(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false)
    .connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(25, TimeUnit.SECONDS).build()) {
    fun load(url: String): ConnectPublicScript? {
        val target = ConnectAssetPolicy.target(url) ?: return null
        val request = Request.Builder().url(target).get()
            .header("Accept", if (target.endsWith(".css")) "text/css" else "text/javascript, application/javascript;q=0.9")
            .header("Origin", ConnectAssetPolicy.ORIGIN).build()
        client.newCall(request).execute().use { response ->
            if (response.code != 200) throw IOException("Official script HTTP ${response.code}")
            if (response.request.url.toString() != target) throw IOException("Unexpected script redirect")
            val mime = response.header("Content-Type").orEmpty()
            val cors = response.header("Access-Control-Allow-Origin").orEmpty()
            if (!ConnectAssetPolicy.validMime(mime, target.endsWith(".css"))) throw IOException("Non-JavaScript response rejected")
            if (!ConnectAssetPolicy.validCors(cors)) throw IOException("Provider CORS permission missing")
            val body = response.body ?: throw IOException("Missing script response")
            if (body.contentLength() > ConnectAssetPolicy.MAX_BYTES) throw IOException("Script exceeds size limit")
            val data = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (data.size() + count > ConnectAssetPolicy.MAX_BYTES) throw IOException("Script exceeds size limit")
                    data.write(buffer, 0, count)
                }
            }
            val bytes = data.toByteArray()
            if (!ConnectAssetPolicy.validBody(bytes)) throw IOException("Empty or HTML script response rejected")
            return ConnectPublicScript(bytes, mime.substringBefore(';').trim(), cors.trim())
        }
    }
}
