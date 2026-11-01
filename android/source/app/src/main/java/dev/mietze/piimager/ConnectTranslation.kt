package dev.mietze.piimager

import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

internal data class ConnectNativeAction(
    val handle: String, val kind: String, val label: String,
    val enabled: Boolean, val reason: String = ""
)
internal data class ConnectNativeDevice(
    val key: String, val name: String, val status: String,
    val detail: ConnectNativeAction?, val shell: ConnectNativeAction?, val screen: ConnectNativeAction?
)
internal data class ConnectNativeState(
    val page: String = "loading", val epoch: String = "", val title: String = "",
    val devices: List<ConnectNativeDevice> = emptyList(), val actions: List<ConnectNativeAction> = emptyList(),
    val viewport: String = "", val notice: String = "", val hasMore: Boolean = false,
    val structure: String = "", val adapterError: String? = null
) {
    val isSession get() = page == "session"
    val renderReady get() = isSession && viewport in setOf("terminal", "screen")
}

/** Deliberately finite parser/action vocabulary. No URL, script or credential from
 * the page is accepted as a command. All handles are transient to one document. */
internal object ConnectTranslationPolicy {
    private val handlePattern = Regex("a_[0-9]{1,8}")
    private val kinds = setOf("detail", "shell", "screen", "signin", "session")
    private val pages = setOf("loading", "signin", "devices", "detail", "session", "unsupported")
    fun handleAllowed(value: String) = handlePattern.matches(value)
    fun clean(value: String, max: Int = 200): String = value.replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim().take(max)
    fun parse(raw: String): ConnectNativeState {
        require(raw.length <= 131_072) { "The Connect page returned too much metadata." }
        val root = JSONObject(raw)
        require(root.optInt("version") == 1) { "The Connect adapter version is not supported." }
        val epoch = root.getString("epoch")
        require(runCatching { UUID.fromString(epoch) }.isSuccess) { "Invalid adapter document." }
        val page = root.optString("page")
        require(page in pages) { "Unknown Connect page state." }
        fun action(obj: JSONObject?): ConnectNativeAction? {
            if (obj == null) return null
            val handle = obj.optString("handle"); val kind = obj.optString("kind")
            require(handleAllowed(handle) && kind in kinds) { "Unrecognized Connect action." }
            return ConnectNativeAction(handle, kind, clean(obj.optString("label"), 100),
                obj.optBoolean("enabled", false), clean(obj.optString("reason"), 260))
        }
        val list = root.optJSONArray("devices")
        require((list?.length() ?: 0) <= 100) { "Too many Connect devices for this view." }
        val devices = (0 until (list?.length() ?: 0)).map { index ->
            val item = list!!.getJSONObject(index)
            val key = item.getString("key")
            require(handleAllowed(key))
            val status = item.optString("status").takeIf { it in setOf("online", "offline", "unknown") } ?: "unknown"
            ConnectNativeDevice(key, clean(item.optString("name"), 120).ifEmpty { "Raspberry Pi" }, status,
                action(item.optJSONObject("detail")), action(item.optJSONObject("shell")), action(item.optJSONObject("screen")))
        }
        require(devices.map { it.key }.distinct().size == devices.size) { "Duplicate device metadata." }
        val steps = root.optJSONArray("actions")
        require((steps?.length() ?: 0) <= 10)
        val viewport = root.optString("viewport")
        require(viewport in setOf("", "terminal", "screen"))
        require(viewport.isEmpty() || page == "session")
        return ConnectNativeState(page, epoch, clean(root.optString("title"), 100), devices,
            (0 until (steps?.length() ?: 0)).mapNotNull { action(steps!!.optJSONObject(it)) },
            viewport, clean(root.optString("notice"), 700), root.optBoolean("hasMore"),
            root.optJSONObject("structure")?.let { s -> JSONObject().apply {
                listOf("deviceLinks", "forms", "terminals", "canvases").forEach { put(it, s.optInt(it).coerceIn(0, 100_000)) }
                if (s.optJSONObject("schema") != null) put("schema", s.getJSONObject("schema"))
            }.toString() }.orEmpty())
    }
    fun clientNotInitialized(action: ConnectNativeAction?): Boolean = action != null && !action.enabled &&
        action.reason.contains("requires JavaScript", ignoreCase = true)
    fun clientNotInitialized(state: ConnectNativeState): Boolean =
        (state.actions + state.devices.flatMap { listOfNotNull(it.shell, it.screen) }).any { clientNotInitialized(it) }
    const val CLIENT_START_ERROR = "The official Connect client has not initialized its connection controls. JavaScript is enabled in Raspberry Manager. Reload the Connect client; if this persists, save connection diagnostics. This is not evidence that the Pi's services are disabled."
    fun unavailable(network: Boolean, status: String, action: ConnectNativeAction?): String? = when {
        !network -> "You're not connected. No Internet connection is available on this phone."
        status == "offline" -> "This Raspberry Pi is reported offline by Connect. Check its power and Internet connection."
        action == null -> "Connect has not provided this action. Open Device details or refresh the device list."
        clientNotInitialized(action) -> CLIENT_START_ERROR
        !action.enabled -> action.reason.ifBlank { "This service is currently unavailable in Raspberry Pi Connect." }
        else -> null
    }
}

/** Origin-checked, top-document-only evaluateJavascript adapter. It exposes no
 * addJavascriptInterface object, privileged bridge, password or token API. */
internal class ConnectTranslator(
    private val view: WebView,
    private val changed: (ConnectNativeState) -> Unit
) {
    private var generation = 0
    private var inFlight = false
    private var stopped = false
    private val source by lazy { view.context.assets.open("connect_translation.js").bufferedReader().use { it.readText() } }
    fun reset() { generation++; inFlight = false }
    fun stop() { stopped = true; reset() }
    fun poll() {
        if (stopped || inFlight || !ConnectPolicy.isConnect(view.url)) return
        val id = generation
        val url = view.url
        val script = source.replace("__EPOCH__", JSONObject.quote(UUID.randomUUID().toString())) +
            "\nJSON.stringify(window.__rmConnectTranslation?.snapshot() ?? null);"
        inFlight = true
        view.evaluateJavascript(script) { result ->
            if (id != generation || stopped) return@evaluateJavascript
            inFlight = false
            if (!ConnectPolicy.isConnect(view.url) || view.url != url) return@evaluateJavascript
            runCatching {
                val raw = JSONTokener(result).nextValue() as? String ?: error("No metadata returned by the Connect page.")
                ConnectTranslationPolicy.parse(raw)
            }.onSuccess(changed).onFailure {
                changed(ConnectNativeState(page = "unsupported", adapterError =
                    "The Connect interface could not be translated. Refresh to retry; no unknown page action was executed."))
            }
        }
    }
    fun perform(epoch: String, action: ConnectNativeAction, result: (String?) -> Unit) {
        if (stopped || !ConnectPolicy.isConnect(view.url) || !ConnectTranslationPolicy.handleAllowed(action.handle)) {
            result("This action is no longer available. Refresh your devices."); return
        }
        val gen = generation
        val script = "JSON.stringify(window.__rmConnectTranslation?.dispatch(${JSONObject.quote(epoch)},${JSONObject.quote(action.handle)}) ?? {ok:false,reason:'stale'});"
        view.evaluateJavascript(script) { raw ->
            if (gen != generation || stopped) return@evaluateJavascript // Expected after a full-page navigation.
            if (!ConnectPolicy.isConnect(view.url)) return@evaluateJavascript
            val ok = runCatching {
                val json = JSONObject(JSONTokener(raw).nextValue() as String)
                json.optBoolean("ok")
            }.getOrDefault(false)
            result(if (ok) null else "Connect could not perform this action. It may have become unavailable; refresh the device list.")
        }
    }
}
