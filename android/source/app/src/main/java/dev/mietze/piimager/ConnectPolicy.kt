package dev.mietze.piimager

import java.net.URI
import java.util.Locale

/** Exact-origin policy. OAuth URLs, cookies and credentials are never exported. */
internal object ConnectPolicy {
    const val ORIGIN = "https://connect.raspberrypi.com"
    const val HOME = "$ORIGIN/"
    const val MAX_WINDOWS = 6
    const val MAX_CLIPBOARD = 65_536
    private val appHosts = setOf("connect.raspberrypi.com", "id.raspberrypi.com")

    fun httpsUri(raw: String?): URI? {
        if (raw.isNullOrEmpty() || raw.length > 16_384 || raw.any { it <= ' ' || it == '\\' }) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null || uri.host.isNullOrEmpty()) return null
        if (uri.port != -1 && uri.port != 443) return null
        return uri
    }
    fun isInternal(raw: String?): Boolean = httpsUri(raw)?.host?.lowercase(Locale.ROOT) in appHosts
    fun isConnect(raw: String?): Boolean = httpsUri(raw)?.host.equals("connect.raspberrypi.com", true)
    fun isAuth(raw: String?): Boolean = httpsUri(raw)?.host.equals("id.raspberrypi.com", true)
    fun isFrameAllowed(raw: String?): Boolean = raw == "about:blank" || raw == "about:srcdoc" || httpsUri(raw) != null
    fun hostLabel(raw: String?): String = httpsUri(raw)?.host?.lowercase(Locale.ROOT) ?: "Connect"
    fun canOpenPopup(source: String?, userGesture: Boolean, count: Int): Boolean =
        userGesture && count in 1 until MAX_WINDOWS && isInternal(source)
    fun routeLabel(raw: String?): String {
        val uri = httpsUri(raw) ?: return "Loading"
        if (isAuth(raw)) return "Raspberry Pi ID"
        if (!isConnect(raw)) return "External page"
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        return when {
            path.contains("sign-in") || path.startsWith("/auth/") -> "Sign-in"
            path.contains("shell") -> "Remote shell"
            path.contains("vnc") || path.contains("screen") -> "Screen sharing"
            path.contains("session") -> "Connect session"
            else -> "Connect"
        }
    }
}
