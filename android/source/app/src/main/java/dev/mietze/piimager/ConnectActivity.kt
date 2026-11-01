package dev.mietze.piimager

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

/**
 * First-party Connect website in a dedicated in-app web profile.
 * No browser-cookie extraction, OAuth client impersonation, native password form or token logging.
 * The provider's real JavaScript/WebRTC client owns the remote connection.
 */
class ConnectActivity : ComponentActivity() {
    companion object { private var profileReady = false }
    internal var ui by mutableStateOf(ConnectUiState())
        private set
    internal var question by mutableStateOf<ConnectQuestion?>(null)
        private set
    private lateinit var host: FrameLayout
    internal var nativeState by mutableStateOf(ConnectNativeState())
        private set
    internal var networkAvailable by mutableStateOf(true)
        private set
    internal var networkLabel by mutableStateOf("Internet")
        private set
    internal var showProviderAuth by mutableStateOf(false)
        private set
    internal var connectionIssue by mutableStateOf<String?>(null)
        private set
    internal var pendingConnection by mutableStateOf<String?>(null)
        private set
    internal var clientResourceIssue by mutableStateOf<String?>(null)
        private set
    private var pendingSince = 0L
    private var nativePopupUntil = 0L
    private val pollHandler = Handler(Looper.getMainLooper())
    private var networkRegistered = false
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val networkListener = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { runOnUiThread { updateNetwork() } }
        override fun onLost(network: Network) { runOnUiThread { updateNetwork() } }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { runOnUiThread { updateNetwork() } }
    }
    private val pollTask = object : Runnable {
        override fun run() {
            if (!foreground || isDestroyed || isFinishing) return
            active?.let { current ->
                if (ConnectPolicy.isConnect(current.view.url) && current.view.progress >= 80) current.translator?.poll()
            }
            if (pendingConnection != null && pendingSince != 0L && SystemClock.elapsedRealtime() - pendingSince > 35_000L) {
                connectionIssue = "Connect did not produce a remote view within 35 seconds. The Pi may be offline, the service may be disabled, or the remote connection may have failed. Being on a different Wi-Fi network alone is not an error."
                pendingConnection = null
            }
            pollHandler.postDelayed(this, if (nativeState.renderReady) 1800L else 900L)
        }
    }
    private data class SessionWindow(
        val id: Int, val view: WebView, var clipboard: ConnectClipboard? = null,
        var title: String = "Connect", var error: String? = null,
        var translator: ConnectTranslator? = null, var translated: ConnectNativeState = ConnectNativeState(),
        var openedFullscreen: Boolean = false, var clientAssetError: String? = null, var uncachedReload: Boolean = false, @Volatile var assetRouteEnabled: Boolean = false
    )
    private val assetLoader by lazy { ConnectAssetWebLoader(filesDir) }
    private val windows = mutableListOf<SessionWindow>()
    private var nextId = 1
    private var activeId = -1
    private var foreground = false
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var filesCallback: ValueCallback<Array<Uri>>? = null
    private var filesOwner: WebView? = null
    private var pendingReport = ""
    private val reportFile get() = File(filesDir, "connect-webview-health.json")
    private val active get() = windows.firstOrNull { it.id == activeId }

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = filesCallback
        val owner = filesOwner
        filesCallback = null; filesOwner = null
        val files = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            ?.filter { it.scheme == "content" }?.take(20)?.toTypedArray()
        callback?.onReceiveValue(if (owner != null && ConnectPolicy.isConnect(owner.url)) files else null)
    }
    private val reportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingReport) }
                ?: error("Cannot open destination")
        }.onSuccess { toast("Report saved") }.onFailure { toast("Report could not be saved") }
        pendingReport = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        host = FrameLayout(this).apply { alpha = 0f }
        runCatching { File(filesDir, "connect-client-errors.txt").writeText("") }
        updateNetwork()
        runCatching { connectivity.registerDefaultNetworkCallback(networkListener); networkRegistered = true }
        // A dedicated process/profile keeps Connect data separate from other app web content.
        runCatching {
            if (!profileReady) {
                WebView.setDataDirectorySuffix("raspberry_connect")
                profileReady = true
            }
            WebView.setWebContentsDebuggingEnabled(false)
            CookieManager.getInstance().setAcceptCookie(true)
            val initial = createWindow()
            val migration = getSharedPreferences("connect_asset_migration", 0)
            if (migration.getInt("origin_route", 0) < 100007) {
                initial.view.clearCache(true) // Dedicated app WebView cache; cookies and layouts stay intact.
                initial.uncachedReload = true
                initial.view.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                migration.edit().putInt("origin_route", 100007).apply()
            }
            initial.view.loadUrl(ConnectPolicy.HOME)
        }.onFailure {
            ui = ui.copy(error = "Android System WebView could not start. Enable or update it and retry.")
        }
        onBackPressedDispatcher.addCallback(this) { goBack() }
        val theme = getSharedPreferences("pi_manager_v1", 0).getString("theme", "Raspberry Dark") ?: "Raspberry Dark"
        setContent { ConnectScreen(this, host, theme) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWindow(): SessionWindow {
        val view = WebView(this)
        val item = SessionWindow(nextId++, view)
        windows += item
        view.setBackgroundColor(android.graphics.Color.WHITE)
        view.isFocusable = true; view.isFocusableInTouchMode = true
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = true // Only files explicitly selected through Android's picker.
            @Suppress("DEPRECATION")
            setAllowFileAccessFromFileURLs(false)
            @Suppress("DEPRECATION")
            setAllowUniversalAccessFromFileURLs(false)
            safeBrowsingEnabled = true
            // Keep the real, stable WebView UA; never pretend to be Chrome to bypass provider checks.
        }
        // Raspberry Pi ID uses a Turnstile challenge in an HTTPS iframe.
        // These cookies remain in this app's dedicated Connect profile.
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        item.clipboard = ConnectClipboard(this, view) { foreground && active?.view === view }
        item.translator = ConnectTranslator(view) { snapshot -> acceptTranslation(item, snapshot) }
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                // onPageStarted owns this volatile flag. Do not read WebView.url
                // from the resource worker thread.
                if (!item.assetRouteEnabled) return null
                return assetLoader.intercept(request)
            }
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (!request.isForMainFrame) return !ConnectPolicy.isFrameAllowed(url)
                if (ConnectPolicy.isInternal(url)) return false // Let WebView keep POST/redirect state.
                if (url == "about:blank") return false // Initial window.open document; no native bridge here.
                if (request.hasGesture() && ConnectPolicy.httpsUri(url) != null) askExternal(url)
                else if (active?.view === v) ui = ui.copy(error = "Navigation outside Raspberry Pi Connect was blocked.")
                return true
            }
            override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                item.assetRouteEnabled = ConnectPolicy.isConnect(url)
                item.clipboard?.close()
                item.translator?.reset()
                item.translated = ConnectNativeState()
                item.clientAssetError = null
                if (active === item) clientResourceIssue = null
                if (active === item) {
                    connectionIssue = null
                    if (ConnectPolicy.isConnect(url) && ConnectPolicy.routeLabel(url) != "Sign-in") showProviderAuth = false
                    if (ConnectPolicy.isAuth(url) || (ConnectPolicy.isConnect(url) && Uri.parse(url).path.orEmpty().startsWith("/auth/"))) showProviderAuth = true
                }
                if (url != "about:blank" && !ConnectPolicy.isInternal(url)) {
                    v.stopLoading()
                    item.error = "Only the official Connect and Raspberry Pi ID pages can open here."
                } else item.error = null
                refreshUi()
            }
            override fun onPageFinished(v: WebView, url: String?) {
                if (!ConnectPolicy.isInternal(url) || !windows.contains(item)) return
                CookieManager.getInstance().flush()
                if (item.uncachedReload) { v.settings.cacheMode = WebSettings.LOAD_DEFAULT; item.uncachedReload = false }
                if (ConnectPolicy.isConnect(url)) {
                    item.clipboard?.attach()
                    item.translator?.poll()
                }
                refreshUi()
                v.postDelayed({ if (windows.contains(item)) probe(v) }, 700)
            }
            override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) { refreshUi() }
            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame && request.url.host == "connect-assets.raspberrypi.com") {
                    runOnUiThread {
                        if (windows.contains(item)) {
                            item.clientAssetError = "A required Connect client resource could not load (network error ${error.errorCode})."
                            if (active === item) clientResourceIssue = item.clientAssetError
                        }
                    }
                }
                if (request.isForMainFrame) {
                    item.error = "Connect could not load (network error ${error.errorCode}). Check your connection and retry."
                    refreshUi()
                }
            }
            override fun onReceivedHttpError(v: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (!request.isForMainFrame && request.url.host == "connect-assets.raspberrypi.com") {
                    runOnUiThread {
                        if (windows.contains(item)) {
                            item.clientAssetError = "A required Connect client resource returned HTTP ${response.statusCode}. The native controls cannot start a session until the official client loads."
                            if (active === item) clientResourceIssue = item.clientAssetError
                        }
                    }
                }
                // Validation errors (e.g. 422) are rendered by the ID page, not hidden behind a native error.
                if (request.isForMainFrame && response.statusCode >= 500) {
                    item.error = "The Connect service returned HTTP ${response.statusCode}. Retry shortly."
                    refreshUi()
                }
            }
            override fun onReceivedSslError(v: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                item.error = "The secure connection could not be verified. The certificate error was not bypassed."
                refreshUi()
            }
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                removeWindow(item)
                ui = ui.copy(error = "The web engine stopped. Retry to reopen Connect; your saved sign-in is retained.")
                return true
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, progress: Int) { refreshUi() }
            override fun onReceivedTitle(v: WebView, title: String?) {
                item.title = title.orEmpty().take(100).ifBlank { "Connect" }
                refreshUi()
            }
            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                if (message != null && ConnectPolicy.isConnect(view.url) && message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    val text = message.message()
                    val category = when {
                        text.contains("CORS", true) -> "script_cors_error"
                        text.contains("Content Security Policy", true) -> "content_security_policy_error"
                        text.contains("SyntaxError", true) -> "javascript_syntax_error"
                        text.contains("TypeError", true) -> "javascript_type_error"
                        else -> "client_console_error"
                    }
                    runCatching { File(filesDir, "connect-client-errors.txt").writeText(category) }
                }
                return true
            }
            override fun onCreateWindow(v: WebView, dialog: Boolean, gesture: Boolean, result: Message): Boolean {
                val nativeGesture = SystemClock.elapsedRealtime() < nativePopupUntil && ConnectPolicy.isConnect(v.url) && windows.size in 1 until ConnectPolicy.MAX_WINDOWS
                if (!ConnectPolicy.canOpenPopup(v.url, gesture, windows.size) && !nativeGesture) return false
                nativePopupUntil = 0L
                val transport = result.obj as? WebView.WebViewTransport ?: return false
                val child = createWindow()
                transport.webView = child.view
                result.sendToTarget()
                return true
            }
            override fun onCloseWindow(window: WebView) {
                windows.firstOrNull { it.view === window }?.let { itemToClose ->
                    if (windows.size > 1) removeWindow(itemToClose) else finish()
                }
            }
            override fun onPermissionRequest(request: PermissionRequest) {
                // Viewing the Pi does NOT need to capture the phone's camera or microphone.
                request.deny()
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
            override fun onShowCustomView(v: View, callback: CustomViewCallback) {
                if (active?.view !== view || !ConnectPolicy.isConnect(view.url) || customView != null) {
                    callback.onCustomViewHidden(); return
                }
                customView = v; customCallback = callback
                host.addView(v, FrameLayout.LayoutParams(-1, -1))
                fullscreen(true)
            }
            override fun onHideCustomView() { leaveFullscreen() }
            override fun onShowFileChooser(v: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                if (active?.view !== v || !ConnectPolicy.isConnect(v.url)) return false
                filesCallback?.onReceiveValue(null)
                filesCallback = callback; filesOwner = v
                return try { filePicker.launch(params.createIntent()); true }
                catch (_: Exception) { filesCallback = null; filesOwner = null; callback.onReceiveValue(null); true }
            }
        }
        view.setDownloadListener { url, _, _, _, _ ->
            if (ConnectPolicy.httpsUri(url) != null) askExternal(url, download = true)
            else toast("This download type is not supported in the Connect window")
        }
        host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        selectWindow(item.id)
        return item
    }

    private fun refreshUi() {
        val current = active
        nativeState = current?.translated ?: ConnectNativeState()
        clientResourceIssue = current?.clientAssetError
        if (ConnectPolicy.isAuth(current?.view?.url)) showProviderAuth = true
        ui = ui.copy(
            host = ConnectPolicy.hostLabel(current?.view?.url),
            route = ConnectPolicy.routeLabel(current?.view?.url),
            progress = (current?.view?.progress ?: 100) / 100f,
            windows = windows.map { ConnectWindowInfo(it.id, it.title, it.id == activeId) },
            error = current?.error
        )
    }
    internal fun selectWindow(id: Int) {
        if (windows.none { it.id == id }) return
        leaveFullscreen()
        activeId = id
        windows.forEach { it.view.visibility = if (it.id == id) View.VISIBLE else View.INVISIBLE }
        active?.view?.bringToFront()
        refreshUi()
    }
    private fun removeWindow(item: SessionWindow) {
        leaveFullscreen()
        item.clipboard?.close()
        item.translator?.stop()
        windows.remove(item)
        host.removeView(item.view)
        runCatching { item.view.destroy() }
        if (activeId == item.id) activeId = windows.lastOrNull()?.id ?: -1
        if (activeId != -1) selectWindow(activeId) else refreshUi()
    }
    internal fun devices() {
        pendingConnection = null; pendingSince = 0L; connectionIssue = null; showProviderAuth = false
        val first = windows.firstOrNull()
        if (first == null) retry() else {
            selectWindow(first.id)
            if (first.translated.page != "devices") first.view.loadUrl(ConnectPolicy.HOME)
            else first.translator?.poll()
        }
    }
    internal fun refreshDevices() {
        val first = windows.firstOrNull()
        if (first != null && first.translated.isSession) {
            ask("Return to devices?", "The current remote session in this window will close. Your sign-in is retained.", "Devices", { retry() })
        } else {
            pendingConnection = null; connectionIssue = null; showProviderAuth = false
            if (first == null) retry() else { selectWindow(first.id); first.view.loadUrl(ConnectPolicy.HOME) }
        }
    }
    internal fun retry() {
        pendingConnection = null; pendingSince = 0L; connectionIssue = null; showProviderAuth = false
        updateNetwork()
        if (windows.isEmpty()) runCatching { createWindow().view.loadUrl(ConnectPolicy.HOME) }
            .onFailure { ui = ui.copy(error = "Android System WebView could not be started.") }
        else {
            active?.error = null
            active?.view?.loadUrl(ConnectPolicy.HOME) // Do not replay a failed POST or OAuth callback.
            refreshUi()
        }
    }
    internal fun recoverClient() {
        ask("Reload the Connect client?",
            "Reload the official client without cached scripts. This can close the current remote connection. Your saved app sign-in and other app data are retained.", "Reload client", {
                val current = active
                if (current == null) retry() else {
                    leaveFullscreen()
                    pendingConnection = null; pendingSince = 0L; connectionIssue = null; clientResourceIssue = null
                    showProviderAuth = false; current.clientAssetError = null; current.uncachedReload = true
                    current.view.clearCache(true)
                    current.view.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    current.view.loadUrl(ConnectPolicy.HOME)
                }
            })
    }
    internal fun reload() {
        ask("Reload Connect?", "Reloading can end the current shell or screen-sharing session.", "Reload", {
            active?.view?.reload()
        })
    }
    internal fun goBack() {
        when {
            ui.fullscreen -> leaveFullscreen()
            pendingConnection != null -> devices()
            windows.size > 1 && active !== windows.firstOrNull() -> closeCurrent()
            active?.view?.canGoBack() == true -> active?.view?.goBack()
            else -> leaveConnect()
        }
    }
    internal fun closeCurrent() {
        val current = active ?: return
        if (windows.size < 2) { leaveConnect(); return }
        ask("Close this Connect window?", "Any remote connection in this window will end. The other windows remain open.", "Close", {
            if (windows.contains(current)) removeWindow(current)
        })
    }
    internal fun leaveConnect() {
        if (nativeState.isSession || pendingConnection != null || windows.size > 1 || ui.route.contains("shell", true) || ui.route.contains("sharing", true) || ui.route.contains("session", true))
            ask("Close Connect?", "All Connect windows and their live connections will close. Your app sign-in is retained.", "Close Connect", { finish() })
        else finish()
    }
    internal fun fullscreen(enabled: Boolean) {
        ui = ui.copy(fullscreen = enabled)
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) bars.hide(WindowInsetsCompat.Type.systemBars()) else bars.show(WindowInsetsCompat.Type.systemBars())
    }
    internal fun leaveFullscreen() {
        customView?.let { host.removeView(it) }
        customView = null
        val callback = customCallback; customCallback = null
        callback?.onCustomViewHidden()
        if (ui.fullscreen) fullscreen(false)
    }
    internal fun keyboard() {
        val view = active?.view ?: return
        view.requestFocus()
        // Focus only the terminal input if present; never read a login field or submit a command.
        if (ConnectPolicy.isConnect(view.url)) view.evaluateJavascript(
            "document.querySelector('.xterm-helper-textarea')?.focus();", null)
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
    internal fun sendKey(key: Int, control: Boolean = false) {
        val view = active?.view ?: return
        if (!ConnectPolicy.isConnect(view.url)) return
        view.requestFocus()
        val now = android.os.SystemClock.uptimeMillis()
        val meta = if (control) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0
        view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0, meta))
        view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0, meta))
    }
    internal fun ask(title: String, message: String, action: String = "Continue", yes: () -> Unit, no: () -> Unit = {}): Boolean {
        if (question != null || isFinishing || isDestroyed) return false
        question = ConnectQuestion(title, message, action, yes, no)
        return true
    }
    internal fun answer(yes: Boolean) {
        val current = question ?: return
        question = null
        if (yes) current.yes() else current.no()
    }
    private fun askExternal(url: String, download: Boolean = false) {
        if (ConnectPolicy.httpsUri(url) == null) return
        ask(if (download) "Download in browser?" else "Open external page?",
            "Open ${ConnectPolicy.hostLabel(url)} in your system browser? The browser has a separate sign-in session.", "Open browser", {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
                    .onFailure { toast("No browser is available") }
            })
    }
    internal fun externalBrowser() { askExternal(ConnectPolicy.HOME) } // Never forward an OAuth code or token URL.
    internal fun forgetSignIn() {
        ask("Forget the app's Connect sign-in?",
            "Close Connect sessions and delete only Raspberry Manager's dedicated Connect web data. Chrome and your saved Pi/Builder data are not affected.", "Forget sign-in", {
                windows.toList().forEach { removeWindow(it) }
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    WebStorage.getInstance().deleteAllData()
                    val item = createWindow()
                    item.view.clearCache(true)
                    item.view.clearHistory()
                    item.view.loadUrl(ConnectPolicy.HOME)
                }
            })
    }
    private fun probe(view: WebView) {
        if (!ConnectPolicy.isInternal(view.url)) return
        val origin = ConnectPolicy.hostLabel(view.url)
        val js = """JSON.stringify({secureContext:window.isSecureContext===true,webRTC:typeof RTCPeerConnection==='function',dataChannel:typeof RTCDataChannel==='function',webSocket:typeof WebSocket==='function',domStorage:typeof localStorage==='object',clipboardAdapter:!!window.__raspberryConnectClipboard,clientRuntime:{readyState:document.readyState,turbo:typeof window.Turbo,stimulus:typeof window.Stimulus,scripts:Array.from(document.scripts).filter(s=>s.src).slice(0,12).map(s=>({host:new URL(s.src).host,file:new URL(s.src).pathname.split('/').pop(),type:s.type})),resources:performance.getEntriesByType('resource').filter(r=>r.initiatorType==='script').slice(0,12).map(r=>({host:new URL(r.name).host,file:new URL(r.name).pathname.split('/').pop(),status:r.responseStatus,size:r.decodedBodySize,duration:Math.round(r.duration)}))}})"""
        view.evaluateJavascript(js) { result ->
            if (!ConnectPolicy.isInternal(view.url) || ConnectPolicy.hostLabel(view.url) != origin) return@evaluateJavascript
            runCatching {
                val json = JSONObject(JSONTokener(result).nextValue() as String)
                    .put("appVersion", ReleaseInfo.VERSION)
                    .put("webViewVersion", WebView.getCurrentWebViewPackage()?.versionName ?: "unknown")
                    .put("androidApi", Build.VERSION.SDK_INT)
                    .put("pageOrigin", origin)
                    .put("pageKind", ConnectPolicy.routeLabel(view.url))
                    .put("windowCount", windows.size)
                    .put("browserCookiesImported", false)
                    .put("livePiConnectionVerified", false)
                    .put("note", "Capability probe, not a successful Pi connection or login test.")
                reportFile.writeText(json.toString(2))
            }
        }
    }
    internal fun healthReport(): String = runCatching { reportFile.readText() }.getOrDefault("No web-engine check has completed yet.") +
        "\n\nNative translation\n" + runCatching { File(filesDir, "connect-native-health.json").readText() }.getOrDefault("No native translation snapshot yet.") +
        "\n\nPublic client asset loader\n" + runCatching { File(filesDir, "connect-asset-health.json").readText() }.getOrDefault("No public script load recorded yet.")
    internal fun exportReport() {
        val text = "Raspberry Manager - Connect diagnostics\n\n" + healthReport()
        val name = "RaspberryManager-Connect-${System.currentTimeMillis()}.txt"
        if (Build.VERSION.SDK_INT < 29) {
            pendingReport = text; reportPicker.launch(name); return
        }
        var inserted: Uri? = null
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RaspberryManager-Connect/")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("No destination")
            inserted = uri
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } ?: error("No stream")
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        }.onSuccess { toast("Saved in Download/RaspberryManager-Connect/$name") }
            .onFailure { inserted?.let { uri -> runCatching { contentResolver.delete(uri, null, null) } }; toast("Report could not be saved") }
    }
    private fun updateNetwork() {
        val capabilities = runCatching { connectivity.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
        networkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        networkLabel = when {
            !networkAvailable -> "Offline"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile data"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "Internet"
        }
    }
    private fun acceptTranslation(item: SessionWindow, snapshot: ConnectNativeState) {
        if (!windows.contains(item) || !ConnectPolicy.isConnect(item.view.url)) return
        item.translated = snapshot
        if (active !== item) return
        nativeState = snapshot
        if (snapshot.page in setOf("devices", "detail", "session")) showProviderAuth = false
        if (snapshot.renderReady) {
            pendingConnection = null; pendingSince = 0L
            if (!item.openedFullscreen) { item.openedFullscreen = true; fullscreen(true) }
        }
        if (snapshot.notice.isNotBlank() && Regex("failed|unable to|could not|disconnected|timed? out|offline|error", RegexOption.IGNORE_CASE).containsMatchIn(snapshot.notice)) {
            connectionIssue = snapshot.notice
            pendingConnection = null
        }
        // Only counts and capabilities are logged: no names, DOM bodies, cookies,
        // terminal output, URLs with query parameters or authentication tokens.
        runCatching {
            val report = JSONObject().put("appVersion", ReleaseInfo.VERSION).put("adapterVersion", 1)
                .put("page", snapshot.page).put("deviceCount", snapshot.devices.size)
                .put("onlineCount", snapshot.devices.count { it.status == "online" })
                .put("offlineCount", snapshot.devices.count { it.status == "offline" })
                .put("recognizedShellControls", snapshot.devices.count { it.shell != null })
                .put("recognizedScreenControls", snapshot.devices.count { it.screen != null })
                .put("shellActions", snapshot.devices.count { it.shell?.enabled == true })
                .put("screenActions", snapshot.devices.count { it.screen?.enabled == true })
                .put("availableStepCount", snapshot.actions.count { it.enabled })
                .put("viewport", snapshot.viewport).put("nativeDeviceSurface", !showProviderAuth && !snapshot.renderReady)
                .put("networkAvailable", networkAvailable).put("networkTransport", networkLabel)
                .put("sameWifiRequired", false).put("authenticationProfilePreserved", true)
                .put("clientNotInitialized", ConnectTranslationPolicy.clientNotInitialized(snapshot))
                .put("clientResourceError", item.clientAssetError ?: JSONObject.NULL)
                .put("adapterError", snapshot.adapterError ?: JSONObject.NULL)
                .put("structure", runCatching { JSONObject(snapshot.structure) }.getOrDefault(JSONObject()))
                .put("piConnectionVerified", false)
                .put("note", "Native adapter metadata. A renderable viewport alone is not proof of a working Pi connection.")
            File(filesDir, "connect-native-health.json").writeText(report.toString(2))
        }
    }
    internal fun signIn() {
        val action = nativeState.actions.firstOrNull { it.kind == "signin" && it.enabled }
        showProviderAuth = true
        if (action != null) runNativeAction(action)
        else active?.view?.loadUrl(ConnectPolicy.ORIGIN + "/sign-in")
    }
    internal fun requestService(device: ConnectNativeDevice, action: ConnectNativeAction?, label: String) {
        val reason = ConnectTranslationPolicy.unavailable(networkAvailable, device.status, action)
        if (reason != null) {
            ask(if (!networkAvailable) "You're not connected." else "$label unavailable", reason,
                "OK", {})
        } else if (action != null) runNativeAction(action, device.status, device.name)
    }
    internal fun runNativeAction(action: ConnectNativeAction, status: String = "unknown", deviceName: String = "") {
        val current = active ?: return
        val reason = ConnectTranslationPolicy.unavailable(networkAvailable, if (action.kind == "detail") "unknown" else status, action)
        if (reason != null) { ask("Action unavailable", reason, "OK", {}); return }
        if (current.translated.epoch.isEmpty() || !ConnectPolicy.isConnect(current.view.url)) return
        connectionIssue = null
        if (action.kind in setOf("shell", "screen", "session")) {
            pendingConnection = when (action.kind) { "shell" -> "Opening remote shell"; "screen" -> "Opening screen sharing"; else -> "Connecting" } +
                if (deviceName.isBlank()) "" else " · $deviceName"
            pendingSince = SystemClock.elapsedRealtime()
        }
        if (action.kind == "signin") showProviderAuth = true
        nativePopupUntil = SystemClock.elapsedRealtime() + 3000L
        current.translator?.perform(current.translated.epoch, action) { error ->
            if (error != null && active === current) { pendingConnection = null; connectionIssue = error }
        }
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    override fun onResume() {
        super.onResume(); foreground = true; windows.forEach { it.view.onResume() }
        updateNetwork(); pollHandler.removeCallbacks(pollTask); pollHandler.post(pollTask)
    }
    override fun onPause() {
        foreground = false
        pollHandler.removeCallbacks(pollTask)
        windows.forEach { it.view.onPause() }
        // Do not use pauseTimers(): it is process-wide and would stop other Connect windows.
        runCatching { CookieManager.getInstance().flush() }
        super.onPause()
    }
    override fun onDestroy() {
        pollHandler.removeCallbacksAndMessages(null)
        if (networkRegistered) runCatching { connectivity.unregisterNetworkCallback(networkListener) }
        filesCallback?.onReceiveValue(null); filesCallback = null
        answer(false)
        leaveFullscreen()
        windows.toList().forEach { removeWindow(it) }
        super.onDestroy()
    }
}
