package dev.mietze.piimager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Raspberry Manager: native Android launcher + actual Pi Imager 0.2 flash wizard. */
class MainActivity : ComponentActivity() {
    private val vm: ManagerViewModel by viewModels()
    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.let { intent ->
                if (intent.resolveActivity(packageManager) != null) intent else
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            splashScreen.setOnExitAnimationListener { splash ->
                splash.animate().alpha(0f).setDuration(160).withEndAction { splash.remove() }.start()
            }
        }
        setTheme(R.style.Theme_PiImager)
        setContent {
            ManagerApp(
                vm = vm,
                startFlash = { startActivity(Intent(this, ImagerActivity::class.java)) },
                startImage = { item -> startActivity(Intent(this, ImagerActivity::class.java).putExtra(LibraryWire.EXTRA_IMAGE, LibraryWire.encode(item))) },
                openConnect = { openConnect() },
                browseStorage = { directoryPicker.launch(null) },
                localDrives = {
                    val storage = getSystemService(StorageManager::class.java)
                    storage.storageVolumes.map { vol ->
                        "${vol.getDescription(this)} • ${if (vol.isRemovable) "Removable" else "Internal"} • ${if (vol.state == "mounted") "Mounted" else vol.state}"
                    }
                }
            )
        }
    }

    private fun openConnect() {
        startActivity(Intent(this, ConnectActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
}

private val PiPink = Color(0xFFE83A67)
private val PiPurple = Color(0xFFB078EB)

internal fun managerPalette(choice: String): ColorScheme = when (choice) {
    "Raspberry OLED" -> darkColorScheme(
        primary = Color(0xFFFF537B), onPrimary = Color(0xFF1C0008),
        background = Color.Black, onBackground = Color(0xFFF7F0F4),
        surface = Color(0xFF0B0B0B), surfaceVariant = Color(0xFF171417),
        onSurface = Color(0xFFF7F0F4), onSurfaceVariant = Color(0xFFD2C5CC),
        secondary = Color(0xFF9ACE68))
    "White" -> lightColorScheme(
        primary = Color(0xFFBA1548), onPrimary = Color.White, background = Color(0xFFF9F9FA),
        surface = Color.White, surfaceVariant = Color(0xFFF1EFF0), secondary = Color(0xFF4A8F3D))
    "Veyra OLED" -> darkColorScheme(
        primary = PiPurple, onPrimary = Color(0xFF16051E),
        secondary = Color(0xFFB69DCB), background = Color.Black, onBackground = Color(0xFFF0EAF3),
        surface = Color(0xFF101013), surfaceVariant = Color(0xFF1A1720),
        onSurface = Color(0xFFF0EAF3), onSurfaceVariant = Color(0xFFCFC1D8))
    else -> darkColorScheme(
        primary = Color(0xFFF04B79), onPrimary = Color(0xFF21040D), background = Color(0xFF101013),
        surface = Color(0xFF1B1B1E), surfaceVariant = Color(0xFF29272D),
        secondary = Color(0xFF9ACA74), onSurface = Color(0xFFEEEAF0))
}

private enum class Tab { HOME, DEVICES, LIBRARY, DRIVES, DESIGNER, SETTINGS }
private enum class DevicePage { ADD, DETAIL, SSH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerApp(
    vm: ManagerViewModel,
    startFlash: () -> Unit,
    startImage: (dev.mietze.piimager.data.OsItem) -> Unit,
    openConnect: () -> Unit,
    browseStorage: () -> Unit,
    localDrives: () -> List<String>
) {
    val state by vm.ui.collectAsState()
    val library: LibraryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var page by remember { mutableStateOf<DevicePage?>(null) }
    var chosen by remember { mutableStateOf<SavedPi?>(null) }
    val preferences = LocalContext.current.getSharedPreferences("pi_manager_v1", 0)
    var showBoot by rememberSaveable { mutableStateOf(preferences.getBoolean("boot_logo", true)) }
    var flashConfirm by remember { mutableStateOf(false) }
    val libraryState by library.ui.collectAsState()
    var berryIsland by remember { mutableStateOf(preferences.getBoolean("berry_island", true)) }
    val navItems = listOf(Triple(Tab.HOME, "Home", Icons.Default.Home),
        Triple(Tab.DEVICES, "Devices", Icons.Default.Devices),
        Triple(Tab.LIBRARY, "Library", Icons.Default.LibraryBooks),
        Triple(Tab.DRIVES, "Drives", Icons.Default.Storage),
        Triple(Tab.DESIGNER, "Builder", Icons.Default.AccountTree),
        Triple(Tab.SETTINGS, "Settings", Icons.Default.Settings))
        .filter { it.first != Tab.LIBRARY || libraryState.enabled }
    LaunchedEffect(libraryState.enabled) {
        if (!libraryState.enabled && tab == Tab.LIBRARY) tab = Tab.HOME
    }
    BackHandler(enabled = !showBoot && (page != null || tab != Tab.HOME)) {
        when (page) {
            DevicePage.SSH -> page = DevicePage.DETAIL
            DevicePage.ADD, DevicePage.DETAIL -> page = null
            null -> tab = Tab.HOME
        }
    }
    MaterialTheme(colorScheme = managerPalette(state.theme)) {
        if (showBoot) { RaspberryBoot { showBoot = false }; return@MaterialTheme }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_raspberry_mark), null, tint = Color.Unspecified, modifier = Modifier.size(30.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Raspberry Manager", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                                Text("Mega Manager X · ${ReleaseInfo.LABEL}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { flashConfirm = true }) { Icon(Icons.Default.Usb, "Flash to USB/SD") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                if (berryIsland) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp).navigationBarsPadding(),
                        contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color(0xFF8B1036),
                            shadowElevation = 15.dp,
                            border = BorderStroke(1.dp, Color(0xFFFF6688).copy(alpha = 0.58f)),
                            modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                navItems.forEach { (t, label, icon) ->
                                    val selected = tab == t
                                    Surface(onClick = {
                                        tab = t; page = null
                                        if (t == Tab.DRIVES) vm.refreshDrives()
                                    }, shape = RoundedCornerShape(21.dp),
                                        color = if (selected) Color(0xFFFFDCE5) else Color.Transparent,
                                        modifier = Modifier.weight(1f)) {
                                        Column(Modifier.padding(vertical = 5.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(icon, label, modifier = Modifier.size(19.dp),
                                                tint = if (selected) Color(0xFF850E32) else Color.White)
                                            Text(label, fontSize = 9.sp, maxLines = 1,
                                                color = if (selected) Color(0xFF850E32) else Color.White,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        navItems.forEach { (t, label, icon) ->
                            NavigationBarItem(selected = tab == t, onClick = {
                                tab = t; page = null
                                if (t == Tab.DRIVES) vm.refreshDrives()
                            }, icon = { Icon(icon, label) }, label = { Text(label) })
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
              Box(Modifier.widthIn(max = 840.dp).fillMaxHeight().fillMaxWidth()) {
                when (tab) {
                    Tab.HOME -> HomePane(state, library, onFlash = { flashConfirm = true }, onAdd = { tab = Tab.DEVICES; page = DevicePage.ADD }, onConnect = openConnect,
                        onDrives = { tab = Tab.DRIVES; vm.refreshDrives() }, onLibrary = { if (libraryState.enabled) tab = Tab.LIBRARY })
                    Tab.DEVICES -> when (page) {
                        DevicePage.ADD -> AddPiPane(onBack = { page = null }, onLogin = openConnect, onAdd = { label, host, user, port ->
                            vm.addDevice(label, host, user, port); page = null
                        })
                        DevicePage.DETAIL -> chosen?.let { pi -> PiDetailPane(pi, { page = null }, {
                            page = DevicePage.SSH
                        }, openConnect, { flashConfirm = true }, { vm.removeDevice(pi.id); page = null }) }
                        DevicePage.SSH -> chosen?.let { pi -> SshPane(pi, onBack = { page = DevicePage.DETAIL }) }
                        null -> DevicesPane(state.devices, onAdd = { page = DevicePage.ADD }, onPick = { pi -> chosen = pi; page = DevicePage.DETAIL }, onConnect = openConnect)
                    }
                    Tab.DRIVES -> DrivesPane(state, onRefresh = vm::refreshDrives, onFlash = { flashConfirm = true }, onBrowse = browseStorage, localDrives = localDrives)
                    Tab.LIBRARY -> OsLibraryPane(library, onSettings = { tab = Tab.SETTINGS }, onFlash = { flashConfirm = true }, onUseImage = startImage)
                    Tab.DESIGNER -> PiMagicDesigner()
                    Tab.SETTINGS -> SettingsPane(state.theme, vm::theme, openConnect, library, berryIsland, { enabled ->
                        berryIsland = enabled; preferences.edit().putBoolean("berry_island", enabled).apply()
                    })
                }
              }
            }
        }
        if (flashConfirm) {
            AlertDialog(
                onDismissRequest = { flashConfirm = false },
                icon = { Icon(Icons.Default.Warning, null) },
                title = { Text("Open Raspberry Pi Imager?") },
                text = { Text("The full native imaging wizard lets you select a Pi, operating system, customization and target disk. No data is erased until you explicitly confirm the write.") },
                confirmButton = { Button(onClick = { flashConfirm = false; startFlash() }) { Text("Open Flash Tools") } },
                dismissButton = { TextButton(onClick = { flashConfirm = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String = "") {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomePane(state: ManagerState, library: LibraryViewModel, onFlash: () -> Unit, onAdd: () -> Unit, onConnect: () -> Unit, onDrives: () -> Unit, onLibrary: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Your Raspberry workspace", "Flash, access and manage your devices in one place.")
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(10.dp))
                Text("Raspberry Pi", fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("Native tools. Real device control.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onFlash, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Usb, null); Spacer(Modifier.width(8.dp)); Text("Flash to USB / microSD")
                }
            }
        }
        if (library.ui.collectAsState().value.enabled) WorkspaceLibrarySummary(state.devices.size, library, onLibrary)
        Text("Quick access", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        ActionCard(Icons.Default.Add, "Add Raspberry Pi", "Sign in to Raspberry Pi Connect or add SSH device", onAdd)
        ActionCard(Icons.Default.Computer, "Raspberry Pi Connect", "In-app Connect · remote shell and screen sharing", onConnect)
        ActionCard(Icons.Default.Storage, "Drive Manager", "Internal storage and removable USB/SD drives", onDrives)
        if (library.ui.collectAsState().value.enabled) ActionCard(Icons.Default.LibraryBooks, "Custom OS Library", "Browse your activated OS sources", onLibrary)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Saved devices: ${state.devices.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Flashing uses the original Pi Imager v0.2 workflow.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun DevicesPane(devices: List<SavedPi>, onAdd: () -> Unit, onPick: (SavedPi) -> Unit, onConnect: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Raspberry devices", "Manage local SSH profiles and Raspberry Pi Connect.")
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add a Raspberry") }
        ActionCard(Icons.Default.Cloud, "Raspberry Pi Connect", "Open your device list and remote sessions inside the app", onConnect)
        if (devices.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("No local SSH profiles yet. Add a Pi to access its native shell, or sign in to Raspberry Pi Connect for your cloud-connected devices.", Modifier.fillMaxWidth().padding(22.dp))
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(devices.size) { i ->
                val pi = devices[i]
                ActionCard(Icons.Default.Dns, pi.label, "${pi.username}@${pi.host}:${pi.port}", { onPick(pi) })
            }
        }
    }
}

@Composable
private fun AddPiPane(onBack: () -> Unit, onLogin: () -> Unit, onAdd: (String, String, String, Int) -> Unit) {
    var label by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        SectionTitle("Add a Raspberry", "Choose your connection method.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp)) {
                Text("Raspberry Pi Connect", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Use the official Raspberry Pi account login. Screen sharing and cloud remote shell continue securely in Raspberry Pi Connect.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Sign in to Raspberry Pi Connect") }
                Text("Your Connect password is never requested by this app.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        Text("Or add a local SSH device", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("IP or hostname") }, placeholder = { Text("mietzberrypi.local") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("SSH username") }, placeholder = { Text("mietzberrypi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, label = { Text("SSH port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            val p = port.toIntOrNull()
            if (host.isBlank() || username.isBlank() || p == null || p !in 1..65535) error = "Enter a hostname, SSH username and valid port."
            else onAdd(label, host, username, p)
        }, modifier = Modifier.fillMaxWidth()) { Text("Save Raspberry") }
        Text("Only device profiles are saved. SSH passwords are entered per session.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun PiDetailPane(pi: SavedPi, onBack: () -> Unit, ssh: () -> Unit, connect: () -> Unit, flash: () -> Unit, delete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        SectionTitle(pi.label, "${pi.username}@${pi.host}:${pi.port}")
        ActionCard(Icons.Default.Terminal, "SSH · Native remote shell", "Encrypted interactive terminal using SSH", ssh)
        ActionCard(Icons.Default.DesktopWindows, "Screen sharing · Pi Connect", "Official Raspberry Pi Connect browser session", connect)
        ActionCard(Icons.Default.Cloud, "Remote shell · Pi Connect", "Connect through your Raspberry Pi account", connect)
        ActionCard(Icons.Default.Usb, "Flash SD / USB", "Open the original Pi Imager wizard", flash)
        Spacer(Modifier.height(15.dp))
        TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Remove saved profile") }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Remove this device?") },
        text = { Text("This deletes only the saved local connection profile, not the Raspberry Pi itself.") },
        confirmButton = { TextButton(onClick = delete) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
private fun SshPane(pi: SavedPi, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as ComponentActivity
    val engine = remember(pi.id) { SshEngine(context.applicationContext) }
    var password by remember(pi.id) { mutableStateOf("") }
    var connected by remember(pi.id) { mutableStateOf(false) }
    var status by remember(pi.id) { mutableStateOf("Not connected") }
    var output by remember(pi.id) { mutableStateOf("Raspberry Manager · SSH terminal\n") }
    var command by remember(pi.id) { mutableStateOf("") }
    var hostPrompt by remember(pi.id) { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    fun ui(action: () -> Unit) = activity.runOnUiThread { action() }
    DisposableEffect(engine) { onDispose { engine.close() } }
    LaunchedEffect(output) { scroll.animateScrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("SSH · ${pi.label}", fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 1)
                Text(status, fontSize = 12.sp, color = if (connected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        if (!connected) {
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("SSH password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = password.isNotEmpty(), onClick = {
                engine.connect(pi, password, scope,
                    onOutput = { msg -> ui { output = (output + msg).takeLast(24000) } },
                    onStatus = { ok, msg -> ui { connected = ok; status = msg; if (!ok) output = (output + "\n$msg\n").takeLast(24000) } },
                    onConfirmHost = { prompt -> ui { hostPrompt = prompt } })
            }, modifier = Modifier.fillMaxWidth()) { Text("Connect over SSH") }
        } else {
            TextButton(onClick = { engine.close(); connected = false; status = "Disconnected" }) { Text("Disconnect") }
        }
        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF090C0B), modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.verticalScroll(scroll).padding(14.dp)) {
                Text(output, color = Color(0xFFD5ECD4), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = command, onValueChange = { command = it }, modifier = Modifier.weight(1f), label = { Text("Command") }, singleLine = true, enabled = connected)
            IconButton(onClick = { if (engine.send(command)) command = "" }, enabled = connected && command.isNotBlank()) { Icon(Icons.Default.Send, "Send") }
        }
        Text("Confirm the SSH host fingerprint when prompted. Passwords are not stored.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
    hostPrompt?.let { prompt ->
        AlertDialog(onDismissRequest = { hostPrompt = null; engine.approveHost(false) },
            title = { Text("Verify SSH host identity") },
            text = { Text(prompt, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { engine.approveHost(true); hostPrompt = null }) { Text("Trust host") } },
            dismissButton = { TextButton(onClick = { engine.approveHost(false); hostPrompt = null }) { Text("Reject") } })
    }
}

@Composable
private fun DrivesPane(state: ManagerState, onRefresh: () -> Unit, onFlash: () -> Unit, onBrowse: () -> Unit, localDrives: () -> List<String>) {
    val context = LocalContext.current
    val androidVolumes = remember { runCatching { localDrives() }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Drive Manager", "Internal storage, USB and removable SD drives")
        ActionCard(Icons.Default.FolderOpen, "Browse device storage", "Android Storage Access Framework", onBrowse)
        Text("Android storage", fontWeight = FontWeight.Bold)
        androidVolumes.forEach { vol ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Text(vol, Modifier.fillMaxWidth().padding(16.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Flashable block devices", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Scan drives") }
        }
        when (state.root) {
            true -> Text("Root available · removable and USB-backed targets only", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            false -> Text("Root permission is required for raw writing and block-device inspection.", fontSize = 12.sp)
            null -> Text("Tap refresh to scan removable drives", fontSize = 12.sp)
        }
        if (state.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.disks.forEach { disk ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(disk.model.ifBlank { disk.name }, fontWeight = FontWeight.Bold)
                    Text("${disk.path}  ·  ${"%.1f".format(disk.sizeBytes / 1_000_000_000.0)} GB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (disk.usbBacked) "USB-backed" else "Removable", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        if (!state.isScanning && state.root == true && state.disks.isEmpty()) Text("No removable/USB-backed block devices found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onFlash, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Usb, null); Spacer(Modifier.width(8.dp)); Text("Flash SD / USB") }
        Text("Actual flashing always goes through the full Pi Imager review and ERASE confirmation.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsPane(theme: String, setTheme: (String) -> Unit, connect: () -> Unit, library: LibraryViewModel, berryIsland: Boolean, setBerryIsland: (Boolean) -> Unit) {
    val themes = listOf("Raspberry Dark", "Raspberry OLED", "White", "Veyra OLED")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReleaseCenter()
        LibrarySettings(library)
        AppStartSettings()
        HorizontalDivider()
        SectionTitle("Appearance", "Raspberry-inspired native Android interface")
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFE83A67))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Berry Island", fontWeight = FontWeight.Bold)
                    Text("Floating raspberry-red navigation with icons and names. Turn off for the classic bar.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = berryIsland, onCheckedChange = setBerryIsland)
            }
        }
        themes.forEach { name ->
            Card(onClick = { setTheme(name) }, colors = CardDefaults.cardColors(containerColor = if (theme == name) MaterialTheme.colorScheme.primary.copy(alpha = 0.19f) else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    if (theme == name) Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        HorizontalDivider()
        ActionCard(Icons.Default.OpenInNew, "Raspberry Pi Connect", "Sign in once inside the app · manage Connect windows", connect)
        Text("Raspberry Manager ${ReleaseInfo.LABEL} · ${ReleaseInfo.VERSION} · ${ReleaseInfo.TITLE}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text("Raspberry Pi Connect belongs to Raspberry Pi Ltd. The official web client runs inside the app. The app sign-in is separate from Chrome; no browser cookies are imported.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}
