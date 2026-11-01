package dev.mietze.piimager

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

internal data class ConnectWindowInfo(val id: Int, val title: String, val active: Boolean)
internal data class ConnectUiState(
    val host: String = "connect.raspberrypi.com", val route: String = "Connect",
    val progress: Float = 0f, val fullscreen: Boolean = false,
    val windows: List<ConnectWindowInfo> = emptyList(), val error: String? = null
)
internal data class ConnectQuestion(val title: String, val message: String, val action: String, val yes: () -> Unit, val no: () -> Unit)
private val Berry = Color(0xFF97173F)

@Composable
internal fun ConnectScreen(activity: ConnectActivity, host: FrameLayout, theme: String) {
    val state = activity.ui
    val native = activity.nativeState
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var keys by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    val failure = if (!activity.networkAvailable) "No Internet connection is available on this phone. Connect works over Wi-Fi or mobile data; the Pi does not need to be on the same Wi-Fi network."
        else state.error ?: activity.connectionIssue ?: native.adapterError
    val showWeb = failure == null && (activity.showProviderAuth || native.renderReady)
    MaterialTheme(colorScheme = managerPalette(theme)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().imePadding()) {
                Column(Modifier.fillMaxSize().then(if (!state.fullscreen) Modifier.safeDrawingPadding() else Modifier)) {
                    if (!state.fullscreen) Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = activity::goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        Surface(shape = RoundedCornerShape(12.dp), color = Berry, modifier=Modifier.size(36.dp)) {
                            Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Memory,null,tint=Color(0xFFFFE2EC),modifier=Modifier.size(22.dp)) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Raspberry Manager", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (activity.showProviderAuth) "Secure sign-in · ${state.host}" else "Raspberry Pi Connect · ${activity.networkLabel}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (state.windows.size > 1) IconButton(onClick = { picker = true }) { Icon(Icons.Default.Tab, "Sessions") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Connect options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Refresh devices") }, onClick = { menu = false; activity.refreshDevices() })
                                DropdownMenuItem(text = { Text("Reload Connect client") }, onClick = { menu = false; activity.recoverClient() })
                                DropdownMenuItem(text = { Text("Connection help") }, onClick = { menu = false; help = true })
                                DropdownMenuItem(text = { Text("Connection diagnostics") }, onClick = { menu = false; diagnostics = true })
                                DropdownMenuItem(text = { Text("Forget app sign-in") }, onClick = { menu = false; activity.forgetSignIn() })
                                DropdownMenuItem(text = { Text("Close Connect") }, onClick = { menu = false; activity.leaveConnect() })
                            }
                        }
                    }
                    if (!state.fullscreen && (state.progress < 1f || activity.pendingConnection != null))
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        // The provider stays laid out and running behind the native surface.
                        // Hidden engine: no pointer, focus or accessibility interaction leaks.
                        AndroidView(factory = { host }, modifier = Modifier.fillMaxSize(), update = {
                            it.alpha = if (showWeb) 1f else 0f
                            it.importantForAccessibility = if (showWeb) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            it.descendantFocusability = if (showWeb) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        })
                        if (!showWeb) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            if (failure != null) NativeConnectMessage(
                                if (!activity.networkAvailable) "You're not connected." else "Connection unavailable",
                                failure, Icons.Default.WifiOff, "Try again", activity::retry,
                                secondary = "Devices", secondaryClick = activity::devices)
                            else NativeConnectPage(activity, native)
                        }
                    }
                    if (native.renderReady && !state.fullscreen) {
                        Surface(color = Berry, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                BerryAction("Devices", Icons.Default.Devices, activity::devices)
                                BerryAction("Keyboard", Icons.Default.Keyboard, activity::keyboard)
                                BerryAction("Keys", Icons.Default.KeyboardCommandKey, { keys = !keys })
                                BerryAction("Full screen", Icons.Default.Fullscreen, { activity.fullscreen(true) })
                            }
                        }
                    }
                    if (native.renderReady && keys) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("Esc" to KeyEvent.KEYCODE_ESCAPE, "Tab" to KeyEvent.KEYCODE_TAB,
                            "↑" to KeyEvent.KEYCODE_DPAD_UP, "↓" to KeyEvent.KEYCODE_DPAD_DOWN,
                            "←" to KeyEvent.KEYCODE_DPAD_LEFT, "→" to KeyEvent.KEYCODE_DPAD_RIGHT).forEach { (label, code) ->
                            TextButton(onClick = { activity.sendKey(code) }) { Text(label) }
                        }
                        TextButton(onClick = { activity.sendKey(KeyEvent.KEYCODE_C, true) }) { Text("Ctrl+C") }
                        TextButton(onClick = { activity.sendKey(KeyEvent.KEYCODE_D, true) }) { Text("Ctrl+D") }
                    }
                }
                if (state.fullscreen) {
                    Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (native.renderReady) SmallBerryButton(Icons.Default.Keyboard, "Keyboard", activity::keyboard)
                        SmallBerryButton(Icons.Default.FullscreenExit, "Show controls", activity::leaveFullscreen)
                    }
                }
            }
        }
        activity.question?.let { q ->
            AlertDialog(onDismissRequest = { activity.answer(false) }, title = { Text(q.title) }, text = { Text(q.message) },
                confirmButton = { TextButton(onClick = { activity.answer(true) }) { Text(q.action) } },
                dismissButton = { TextButton(onClick = { activity.answer(false) }) { Text("Cancel") } })
        }
        if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Text("Connect sessions") },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                state.windows.forEachIndexed { index, item ->
                    TextButton(onClick = { picker = false; activity.selectWindow(item.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (index == 0) "Devices" else "Session $index", Modifier.weight(1f))
                        if (item.active) Icon(Icons.Default.Check, "Active")
                    }
                }
                if (state.windows.size > 1) TextButton(onClick = { picker = false; activity.closeCurrent() }) { Text("Close current session") }
            } }, confirmButton = { TextButton(onClick = { picker = false }) { Text("Done") } })
        if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("Native Connect controls") },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your device list and buttons are native Android controls. They trigger the existing controls of the official Connect client in the background.")
                Text("The website's menu, navigation and help footer are not the app interface. Only the terminal or remote desktop viewport becomes visible during a session.")
                Text("Connect works over the Internet, including mobile data. It does not require the Pi and this phone to share a Wi-Fi network.")
                Text("Unavailable actions are not bypassed. A Pi can be online while screen sharing or shell is not offered. Screen sharing requires an active Wayland desktop; OS Lite offers shell only.")
                Text("The official sign-in is visible only when authentication is required. Your existing app sign-in is retained; Chrome cookies are not imported.")
                Text("If Raspberry Pi changes the website structure, its adapter may need an update. Unknown actions are never guessed or auto-executed.")
            } }, confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } })
        if (diagnostics) AlertDialog(onDismissRequest = { diagnostics = false }, title = { Text("Connect diagnostics") },
            text = { Text(activity.healthReport(), Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), fontSize = 12.sp) },
            confirmButton = { TextButton(onClick = activity::exportReport) { Text("Save to Downloads") } },
            dismissButton = { TextButton(onClick = { diagnostics = false }) { Text("Close") } })
    }
}

@Composable
private fun NativeConnectPage(activity: ConnectActivity, state: ConnectNativeState) {
    if (activity.pendingConnection != null && !state.renderReady) {
        Column(Modifier.fillMaxSize().padding(26.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Text(activity.pendingConnection ?: "Connecting", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("The official Connect client is establishing the session.", Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.actions.filter { it.kind == "session" }.forEach { action ->
                Button(onClick = { activity.runNativeAction(action) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(action.label) }
            }
            if (state.notice.isNotBlank()) Text(state.notice, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = activity::devices, modifier = Modifier.padding(top = 14.dp)) { Text("Back to devices") }
        }
        return
    }
    when (state.page) {
        "loading" -> Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Loading your Raspberry devices…", Modifier.padding(top = 18.dp), fontSize = 16.sp)
        }
        "signin" -> NativeConnectMessage("Your Raspberry. Anywhere.",
            "Sign in with Raspberry Pi ID to load your devices. After sign-in, you return to the native Raspberry Manager controls.",
            Icons.Default.AccountCircle, "Sign in with Raspberry Pi ID", activity::signIn)
        "devices" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var search by rememberSaveable { mutableStateOf("") }
            var filter by rememberSaveable { mutableStateOf("All") }
            val online=state.devices.count{it.status=="online"}
            val offline=state.devices.count{it.status=="offline"}
            val visible=state.devices.filter{device->
                (filter=="All" || (filter=="Online"&&device.status=="online") ||
                 (filter=="Offline"&&device.status=="offline") ||
                 (filter=="Unknown"&&device.status=="unknown")) &&
                device.name.contains(search.trim(),ignoreCase=true)
            }.sortedWith(compareBy<ConnectNativeDevice>{if(it.status=="online")0 else if(it.status=="unknown")1 else 2}.thenBy{it.name.lowercase()})
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Raspberry Pi Connect",fontSize = 22.sp,fontWeight = FontWeight.Bold)
                    Text("${state.devices.size} linked · $online online · $offline offline",fontSize = 12.sp,color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = activity::refreshDevices,
                    colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=Berry.copy(alpha=.24f))) {
                    Icon(Icons.Default.Refresh,"Refresh devices",tint=MaterialTheme.colorScheme.primary)
                }
            }
            OutlinedTextField(search,{search=it.take(120)},Modifier.fillMaxWidth(),singleLine=true,
                label={Text("Find a Raspberry Pi")},shape=RoundedCornerShape(16.dp),
                leadingIcon={Icon(Icons.Default.Search,null)},
                trailingIcon={if(search.isNotBlank())IconButton(onClick={search=""}){Icon(Icons.Default.Close,"Clear search")}})
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("All","Online","Offline","Unknown").forEach{f->
                    FilterChip(selected=filter==f,onClick={filter=f},label={Text(f)})
                }
            }
            if(state.notice.isNotBlank())Text(state.notice,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
            if(ConnectTranslationPolicy.clientNotInitialized(state) || activity.clientResourceIssue != null) {
                Card(shape = RoundedCornerShape(18.dp),colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Connect needs attention",fontWeight=FontWeight.Bold)
                        Text(activity.clientResourceIssue ?: ConnectTranslationPolicy.CLIENT_START_ERROR,fontSize=12.sp)
                        TextButton(onClick=activity::recoverClient){Text("Reload the official client")}
                    }
                }
            }
            if(visible.isEmpty()){
                Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),
                    color=MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Devices,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(32.dp))
                        Text(if(state.devices.isEmpty())"No linked Raspberry Pi" else "No devices match your filters",
                            fontWeight=FontWeight.Bold)
                        Text(if(state.devices.isEmpty())
                            "Link a Pi through your Raspberry Pi ID and refresh this screen."
                            else "Try a different search or show all devices.",
                            color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
                        OutlinedButton(onClick={if(state.devices.isEmpty())activity.refreshDevices() else {search="";filter="All"}}) {
                            Text(if(state.devices.isEmpty())"Refresh devices" else "Clear filters")
                        }
                    }
                }
            }else{
                BoxWithConstraints(Modifier.fillMaxWidth()){
                    val columns=if(maxWidth>=730.dp)2 else 1
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                        visible.chunked(columns).forEach{row->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                row.forEach{device->Box(Modifier.weight(1f)){NativeDeviceCard(device,activity)}}
                                if(row.size<columns)Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Text("${visible.size} shown · sessions use the official Raspberry Pi Connect WebRTC client.",
                fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.hasMore)Text("Connect returned the first 100 devices.",fontSize=11.sp)
        }
        "detail", "session" -> Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(if (state.page == "session") Icons.Default.Computer else Icons.Default.Memory, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(state.title.ifBlank { if (state.page == "session") "Connect session" else "Device actions" }, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(if (state.page == "session") "Waiting for the remote view. The connection is not yet confirmed." else "Available actions from your Raspberry Pi Connect account.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.notice.isNotBlank()) Text(state.notice, color = MaterialTheme.colorScheme.error)
            state.actions.filter { it.kind != "signin" }.forEach { action ->
                Button(onClick = { activity.runNativeAction(action) }, modifier = Modifier.fillMaxWidth()) { Text(action.label) }
            }
            TextButton(onClick = activity::devices) { Text("Back to devices") }
        }
        else -> NativeConnectMessage("Connect needs an adapter update",
            "This page could not be mapped to known device controls. Nothing was clicked automatically. Refresh to try again or save diagnostics from the menu.",
            Icons.Default.Extension, "Refresh devices", activity::refreshDevices)
    }
}

@Composable
private fun NativeDeviceCard(device: ConnectNativeDevice, activity: ConnectActivity) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .28f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Berry.copy(alpha = .3f), shape = RoundedCornerShape(17.dp)) {
                    Icon(Icons.Default.Memory, null, Modifier.padding(14.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val status = when (device.status) { "online" -> "Online"; "offline" -> "Offline"; else -> "Status not confirmed" }
                    Text(status, fontSize = 12.sp, color = when(device.status) {
                        "online" -> MaterialTheme.colorScheme.secondary; "offline" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant
                    })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NativeServiceButton("Remote shell", Icons.Default.Terminal, device, device.shell, activity, Modifier.weight(1f))
                NativeServiceButton("Screen sharing", Icons.Default.DesktopWindows, device, device.screen, activity, Modifier.weight(1f))
            }
            if (device.shell?.enabled != true || device.screen?.enabled != true) Text(
                "Tap an unavailable action to see the reason reported by Connect.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (device.detail != null) TextButton(onClick = { activity.runNativeAction(device.detail, device.status, device.name) }, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Info, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Device details", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun NativeServiceButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, device: ConnectNativeDevice,
    action: ConnectNativeAction?, activity: ConnectActivity, modifier: Modifier) {
    val available = action?.enabled == true && device.status != "offline" && activity.networkAvailable
    Surface(onClick = { activity.requestService(device, action, label) }, shape = RoundedCornerShape(17.dp),
        color = if (available) Berry else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (available) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.heightIn(min = 82.dp)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(24.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}

@Composable
private fun NativeConnectMessage(title: String, message: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: String, click: () -> Unit, secondary: String? = null, secondaryClick: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(24.dp), color = Berry.copy(alpha = .22f)) {
            Icon(icon, null, Modifier.padding(22.dp).size(46.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(22.dp))
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = click, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(action) }
        if (secondary != null) TextButton(onClick = secondaryClick) { Text(secondary) }
    }
}
@Composable
private fun SmallBerryButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, click: () -> Unit) {
    FilledTonalIconButton(onClick = click, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Berry.copy(alpha = .93f), contentColor = Color.White)) { Icon(icon, label) }
}
@Composable
private fun BerryAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    TextButton(onClick = click, colors = ButtonDefaults.textButtonColors(contentColor = Color.White), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
        Icon(icon, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(label, fontSize = 11.sp)
    }
}
