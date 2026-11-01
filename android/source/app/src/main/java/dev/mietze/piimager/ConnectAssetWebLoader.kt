package dev.mietze.piimager

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONArray

/** Called on WebView's resource worker, never on the Android UI thread. */
internal class ConnectAssetWebLoader(private val directory: File, private val source: ConnectAssetSource = ConnectAssetSource()) {
    private val observations = linkedMapOf<String, JSONObject>()
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val target = ConnectAssetPolicy.target(url, request.method, request.isForMainFrame) ?: return null
        return try {
            val script = source.load(url) ?: return null
            val sha = MessageDigest.getInstance("SHA-256").digest(script.bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            record(JSONObject().put("asset", target.substringAfterLast('/')).put("result", "loaded_from_official_origin")
                .put("mime", script.mime).put("bytes", script.bytes.size).put("sha256", sha).put("credentialsForwarded", false)
                .put("scriptModified", false).put("providerCorsValidated", true))
            WebResourceResponse(script.mime, "UTF-8", 200, "OK", mapOf(
                "Access-Control-Allow-Origin" to script.cors, "Cache-Control" to "no-store",
                "X-Content-Type-Options" to "nosniff"), ByteArrayInputStream(script.bytes))
        } catch (error: Exception) {
            // No URL, request headers or body is retained on failure. A failed
            // alternate route falls back to normal WebView loading, never fake JS.
            record(JSONObject().put("asset", target.substringAfterLast('/')).put("result", "official_origin_unavailable")
                .put("failureType", error.javaClass.simpleName).put("normalWebViewFallback", true))
            null
        }
    }
    @Synchronized private fun record(info: JSONObject) {
        runCatching {
            observations[info.getString("asset")] = info
            while (observations.size > 12) observations.remove(observations.keys.first())
            File(directory, "connect-asset-health.json").writeText(JSONObject()
                .put("appVersion", ReleaseInfo.VERSION).put("assets", JSONArray(observations.values.toList())).toString(2))
        }
    }
}
