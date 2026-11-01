package dev.mietze.piimager

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebView
import org.json.JSONObject
import java.util.UUID

/** A main-frame MessagePort, addressed to ONE exact HTTPS origin, not a Java JS interface. */
internal class ConnectClipboard(
    private val activity: ConnectActivity,
    private val view: WebView,
    private val isCurrent: () -> Boolean
) {
    private var port: WebMessagePort? = null
    private var epoch = 0
    private var pending = false

    fun close() {
        epoch++
        pending = false
        runCatching { port?.close() }
        port = null
    }

    fun attach() {
        if (!ConnectPolicy.isConnect(view.url)) return
        close()
        val generation = epoch
        val nonce = UUID.randomUUID().toString()
        val script = activity.assets.open("connect_clipboard.js").bufferedReader().use { it.readText() }
            .replace("__CHANNEL_NONCE__", nonce)
        view.evaluateJavascript(script) {
            if (epoch != generation || !ConnectPolicy.isConnect(view.url)) return@evaluateJavascript
            val pair = view.createWebMessageChannel()
            port = pair[0]
            pair[0].setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
                override fun onMessage(channel: WebMessagePort, message: WebMessage) {
                    receive(channel, message.data.orEmpty(), generation)
                }
            })
            view.postWebMessage(WebMessage(nonce, arrayOf(pair[1])), Uri.parse(ConnectPolicy.ORIGIN))
        }
    }

    private fun receive(channel: WebMessagePort, raw: String, generation: Int) {
        if (generation != epoch || !isCurrent() || !ConnectPolicy.isConnect(view.url)) return
        if (raw.length > ConnectPolicy.MAX_CLIPBOARD * 6 + 512) return
        val data = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val id = data.optInt("id", -1)
        if (id !in 1..1_000_000) return
        fun reply(ok: Boolean, text: String = "") {
            if (generation != epoch) return
            runCatching { channel.postMessage(WebMessage(JSONObject().put("id", id).put("ok", ok).put("text", text).toString())) }
        }
        if (pending) { reply(false); return }
        val kind = data.optString("kind")
        val text = data.optString("text")
        if (kind !in setOf("read", "write") || text.length > ConnectPolicy.MAX_CLIPBOARD) { reply(false); return }
        pending = true
        val message = if (kind == "read")
            "Allow the current Connect session to read text from your phone's clipboard? Text may be sent to your Raspberry Pi."
        else "Copy ${text.length} characters from Connect to your phone's clipboard?"
        val accepted = activity.ask("Connect clipboard", message, if (kind == "read") "Allow once" else "Copy", {
            pending = false
            if (generation != epoch || !isCurrent() || !ConnectPolicy.isConnect(view.url)) { reply(false); return@ask }
            val clipboard = activity.getSystemService(ClipboardManager::class.java)
            if (kind == "write") {
                clipboard.setPrimaryClip(ClipData.newPlainText("Raspberry Pi Connect", text))
                reply(true)
            } else {
                val clip = clipboard.primaryClip
                // Text only: never dereference clipboard content:// URIs.
                val value = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString().orEmpty() else ""
                if (value.length > ConnectPolicy.MAX_CLIPBOARD) reply(false) else reply(true, value)
            }
        }, { pending = false; reply(false) })
        if (!accepted) { pending = false; reply(false) }
    }
}
