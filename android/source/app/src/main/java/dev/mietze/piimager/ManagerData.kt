package dev.mietze.piimager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mietze.piimager.data.StorageTarget
import dev.mietze.piimager.system.RootStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local device profiles. Passwords and Raspberry Connect tokens are never persisted. */
data class SavedPi(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val host: String,
    val username: String,
    val port: Int = 22
)

data class ManagerState(
    val devices: List<SavedPi> = emptyList(),
    val theme: String = "Raspberry Dark",
    val root: Boolean? = null,
    val disks: List<StorageTarget> = emptyList(),
    val isScanning: Boolean = false,
    val error: String? = null
)

class ManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("pi_manager_v1", 0)
    private val _ui = MutableStateFlow(ManagerState(devices = loadDevices(), theme = prefs.getString("theme", "Raspberry Dark") ?: "Raspberry Dark"))
    val ui = _ui.asStateFlow()

    fun theme(name: String) {
        prefs.edit().putString("theme", name).apply()
        _ui.update { it.copy(theme = name) }
    }

    fun addDevice(label: String, host: String, user: String, port: Int) {
        require(host.isNotBlank() && user.isNotBlank()) { "Host and username are required" }
        require(port in 1..65535) { "Invalid SSH port" }
        val next = _ui.value.devices + SavedPi(label = label.trim().ifBlank { host.trim() }, host = host.trim(), username = user.trim(), port = port)
        saveDevices(next)
    }

    fun removeDevice(id: String) = saveDevices(_ui.value.devices.filterNot { it.id == id })

    fun refreshDrives() {
        _ui.update { it.copy(isScanning = true, error = null) }
        viewModelScope.launch {
            val root = runCatching { RootStorage.hasRoot() }.getOrDefault(false)
            val drives = if (root) runCatching { RootStorage.scanTargets() }.getOrElse {
                _ui.update { state -> state.copy(error = it.message ?: "Unable to scan removable devices") }
                emptyList()
            } else emptyList()
            _ui.update { it.copy(root = root, disks = drives, isScanning = false) }
        }
    }

    private fun saveDevices(devices: List<SavedPi>) {
        val arr = JSONArray()
        devices.forEach { d -> arr.put(JSONObject().apply {
            put("id", d.id); put("label", d.label); put("host", d.host); put("user", d.username); put("port", d.port)
        }) }
        prefs.edit().putString("devices", arr.toString()).apply()
        _ui.update { it.copy(devices = devices) }
    }

    private fun loadDevices(): List<SavedPi> = runCatching {
        val arr = JSONArray(prefs.getString("devices", "[]") ?: "[]")
        (0 until arr.length()).map { index ->
            val d = arr.getJSONObject(index)
            SavedPi(d.optString("id", UUID.randomUUID().toString()), d.optString("label", "Raspberry Pi"),
                d.getString("host"), d.optString("user", "pi"), d.optInt("port", 22))
        }
    }.getOrDefault(emptyList())
}
