package dev.mietze.piimager

import android.content.Context
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Real SSH session, strict TOFU host-key approval, private known_hosts and ephemeral passwords. */
class SshEngine(private val context: Context) {
    private var session: Session? = null
    private var shell: ChannelShell? = null
    private var worker: Job? = null
    private var pending: LinkedBlockingQueue<Boolean>? = null

    fun connect(
        pi: SavedPi,
        password: String,
        scope: CoroutineScope,
        onOutput: (String) -> Unit,
        onStatus: (Boolean, String) -> Unit,
        onConfirmHost: (String) -> Unit
    ) {
        close()
        worker = scope.launch(Dispatchers.IO) {
            try {
                val jsch = JSch()
                val known = File(context.filesDir, "known_hosts")
                if (!known.exists()) known.createNewFile()
                jsch.setKnownHosts(known.absolutePath)
                val s = jsch.getSession(pi.username, pi.host, pi.port)
                session = s
                s.setConfig("StrictHostKeyChecking", "ask")
                s.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
                s.setPassword(password)
                s.setUserInfo(object : UserInfo {
                    override fun getPassword() = password
                    override fun getPassphrase(): String? = null
                    override fun promptPassphrase(message: String?) = false
                    override fun promptPassword(message: String?) = true
                    override fun showMessage(message: String?) { onOutput("\n${message.orEmpty()}\n") }
                    override fun promptYesNo(message: String?): Boolean {
                        val q = LinkedBlockingQueue<Boolean>(1)
                        pending = q
                        onConfirmHost(message ?: "Trust this SSH host key?")
                        return q.poll(60, TimeUnit.SECONDS) == true
                    }
                })
                onStatus(false, "Connecting to ${pi.host}:${pi.port}...")
                s.connect(12000)
                val c = s.openChannel("shell") as ChannelShell
                shell = c
                c.setPty(true)
                c.setPtyType("xterm")
                val reader = c.inputStream
                c.connect(10000)
                onStatus(true, "Connected securely to ${pi.label}")
                val buffer = ByteArray(4096)
                while (c.isConnected && !Thread.currentThread().isInterrupted) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (count > 0) onOutput(String(buffer, 0, count, Charsets.UTF_8))
                }
                onStatus(false, "SSH session closed")
            } catch (t: Exception) {
                onStatus(false, "SSH: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                shell?.disconnect(); session?.disconnect()
                shell = null; session = null; pending = null
            }
        }
    }

    fun approveHost(approved: Boolean) { pending?.offer(approved) }

    fun send(command: String): Boolean = runCatching {
        val c = shell ?: return false
        if (!c.isConnected) return false
        c.outputStream.write((command + "\n").toByteArray(Charsets.UTF_8))
        c.outputStream.flush()
        true
    }.getOrDefault(false)

    fun close() {
        pending?.offer(false)
        shell?.disconnect(); session?.disconnect()
        shell = null; session = null
        worker?.cancel(); worker = null
    }
}
