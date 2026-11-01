package dev.mietze.piimager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.mietze.piimager.managerPalette
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.mietze.piimager.ImagerUiState
import dev.mietze.piimager.ImagerViewModel
import dev.mietze.piimager.WizardStep
import dev.mietze.piimager.data.FlashStage
import dev.mietze.piimager.data.ImageSource
import dev.mietze.piimager.data.OsItem
import dev.mietze.piimager.data.PiDevice
import dev.mietze.piimager.data.StorageTarget
import dev.mietze.piimager.data.flattenOs
import dev.mietze.piimager.system.ImageWriter

private val RaspberryRed = Color(0xFFD31346)
private val RaspberryRedDark = Color(0xFFFF5A7D)
private val RaspberryGreen = Color(0xFF75A928)

private val LightColors = lightColorScheme(
    primary = RaspberryRed,
    secondary = RaspberryGreen,
    surface = Color(0xFFFCFCFC),
    surfaceVariant = Color(0xFFF1F1F3),
    background = Color(0xFFF7F7F8)
)

private val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = RaspberryRedDark,
    secondary = Color(0xFF9CCB67),
    surface = Color(0xFF171719),
    surfaceVariant = Color(0xFF242428),
    background = Color(0xFF0E0E10)
)

@Composable
fun PiImagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = managerPalette(LocalContext.current.getSharedPreferences("pi_manager_v1", 0).getString("theme", "Raspberry Dark") ?: "Raspberry Dark"),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiImagerApp(
    viewModel: ImagerViewModel,
    onPickLocalImage: () -> Unit
) {
    val state by viewModel.ui.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    PiImagerTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(RaspberryRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("π", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Raspberry Pi Imager", fontWeight = FontWeight.Bold)
                                Text(
                                    "Android edition",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        RootBadge(state.rootAvailable)
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                StepBar(
                    current = state.step,
                    onStep = viewModel::goTo
                )
                HorizontalDivider()
                when (state.step) {
                    WizardStep.DEVICE -> DeviceScreen(state, viewModel)
                    WizardStep.OS -> OsScreen(state, viewModel, onPickLocalImage)
                    WizardStep.CUSTOMIZE -> CustomizationScreen(state, viewModel)
                    WizardStep.STORAGE -> StorageScreen(state, viewModel)
                    WizardStep.REVIEW -> ReviewScreen(state, viewModel)
                    WizardStep.WRITE -> WriteScreen(state, viewModel)
                }
            }
        }

        if (showSettings) {
            SettingsSheet(
                state = state,
                onDismiss = { showSettings = false },
                onSetRepo = viewModel::setRepoUrl,
                onSetVerify = viewModel::setVerify,
                onRefresh = { viewModel.loadCatalog(true) }
            )
        }
    }
}

@Composable
private fun RootBadge(root: Boolean?) {
    val text = when (root) {
        true -> "ROOT"
        false -> "NO ROOT"
        null -> "..."
    }
    val color = if (root == true) RaspberryGreen else MaterialTheme.colorScheme.outline
    Text(
        text,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepBar(current: WizardStep, onStep: (WizardStep) -> Unit) {
    val steps = listOf(
        WizardStep.DEVICE to "Device",
        WizardStep.OS to "OS",
        WizardStep.CUSTOMIZE to "Customise",
        WizardStep.STORAGE to "Storage",
        WizardStep.REVIEW to "Review",
        WizardStep.WRITE to "Writing"
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        steps.forEachIndexed { index, pair ->
            val active = pair.first == current
            val enabled = pair.first.ordinal <= current.ordinal && current != WizardStep.WRITE
            Row(
                modifier = Modifier
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(50)
                    )
                    .clickable(enabled = enabled) { onStep(pair.first) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    (index + 1).toString(),
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    pair.second,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DeviceScreen(state: ImagerUiState, vm: ImagerViewModel) {
    ScreenHeader(
        title = "Select your Raspberry Pi",
        subtitle = state.libraryItem?.let { "Selected: ${it.name}" } ?: "Choose the device you want to prepare."
    )
    if (state.libraryItem != null) {
        TextButton(onClick = vm::clearLibraryItem) { Text("Choose a different OS instead") }
    }

    when {
        state.loadingCatalog -> LoadingState("Loading official Raspberry Pi catalogue…")
        state.catalogError != null -> ErrorState(state.catalogError) { vm.loadCatalog(true) }
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.catalog?.devices.orEmpty().filter { device ->
                val tags = state.libraryItem?.devices.orEmpty()
                tags.isEmpty() || device.tags.isEmpty() || tags.any(device.tags::contains)
            }, key = { it.name }) { device ->
                DeviceCard(device) { vm.selectDevice(device) }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: PiDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (device.icon != null) {
                AsyncImage(
                    model = device.icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
            } else {
                Icon(
                    Icons.Default.Computer,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    device.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun OsScreen(
    state: ImagerUiState,
    vm: ImagerViewModel,
    onPickLocalImage: () -> Unit
) {
    val rootItems = remember(state.catalog, state.selectedDevice) { vm.filteredOs() }
    var stack by remember(state.selectedDevice, state.catalog) { mutableStateOf(emptyList<OsItem>()) }
    var query by remember { mutableStateOf("") }

    val currentItems = if (query.isNotBlank()) {
        flattenOs(rootItems).filter {
            it.name.contains(query, true) || it.description.contains(query, true)
        }
    } else {
        stack.lastOrNull()?.subitems ?: rootItems
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = if (stack.isEmpty()) "Choose operating system" else stack.last().name,
            subtitle = state.selectedDevice?.name ?: "Select an image"
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stack.isNotEmpty()) {
                IconButton(onClick = { stack = stack.dropLast(1) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search operating systems") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (stack.isEmpty() && query.isBlank()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onPickLocalImage),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(44.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Use custom image", fontWeight = FontWeight.Bold)
                                Text("Select .img, .zip, .xz or .gz from this phone")
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }

            items(currentItems, key = { (it.url ?: "cat:") + it.name }) { os ->
                OsCard(
                    os = os,
                    onClick = {
                        if (os.isCategory) {
                            query = ""
                            stack = stack + os
                        } else {
                            vm.selectOs(os)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OsCard(os: OsItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (os.icon != null) {
                AsyncImage(
                    model = os.icon,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp)
                )
            } else {
                Icon(Icons.Default.Memory, null, Modifier.size(46.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(os.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (os.description.isNotBlank()) {
                    Text(
                        os.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!os.isCategory && (os.downloadSize != null || os.releaseDate != null)) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        listOfNotNull(
                            os.downloadSize?.let { ImageWriter.formatBytes(it) + " download" },
                            os.releaseDate
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun StorageScreen(state: ImagerUiState, vm: ImagerViewModel) {
    ScreenHeader(
        title = "Choose storage",
        subtitle = "Only removable or USB-backed block devices are shown."
    )

    if (state.rootAvailable != true) {
        WarningCard(
            "Root access required",
            "Android does not normally allow apps to write raw disk images. Grant root access so the Imager can safely access an external SD-card reader or USB drive."
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(onClick = vm::scanStorage) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(7.dp))
            Text("Scan storage")
        }
    }

    if (state.scanningStorage) {
        LoadingState("Scanning removable storage…")
        return
    }

    if (state.targets.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Usb,
                null,
                Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text("No removable storage detected", fontWeight = FontWeight.Bold)
            Text(
                "Connect a microSD reader or USB drive, then tap Scan storage.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.targets, key = { it.path }) { target ->
                StorageCard(target) { vm.selectTarget(target) }
            }
        }
    }
}

@Composable
private fun StorageCard(target: StorageTarget, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (target.usbBacked) Icons.Default.Usb else Icons.Default.Storage,
                null,
                Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (target.vendor + " " + target.model).trim(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    ImageWriter.formatBytes(target.sizeBytes) + " • " + target.path,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (target.usbBacked) {
                    Text(
                        "USB-backed",
                        color = RaspberryGreen,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun ReviewScreen(state: ImagerUiState, vm: ImagerViewModel) {
    var showConfirm by remember { mutableStateOf(false) }

    ScreenHeader(
        title = "Ready to write",
        subtitle = "Review everything before erasing the selected storage."
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SummaryCard("Device", state.selectedDevice?.name ?: "Not selected")
        }
        item {
            SummaryCard("Operating system", sourceName(state.selectedSource))
        }
        item {
            SummaryCard(
                "Storage",
                state.selectedTarget?.let {
                    (it.vendor + " " + it.model).trim() + " • " + ImageWriter.formatBytes(it.sizeBytes)
                } ?: "Not selected"
            )
        }
        item {
            val fmt = (state.selectedSource as? ImageSource.Remote)?.item?.initFormat ?: "none"
            val detail = when {
                fmt == "none" || fmt.isBlank() -> "Not supported by this image"
                !state.customization.enabled -> "Skipped"
                else -> "Enabled • " + fmt
            }
            SummaryCard("OS customisation", detail)
        }
        item {
            WarningCard(
                "All data on the selected storage will be erased",
                "The app deliberately refuses to list normal internal Android storage. Still verify the model and capacity above before continuing."
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Verify after writing", fontWeight = FontWeight.Bold)
                    Text(
                        "Read the image back and compare SHA-256.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.verifyAfterWrite,
                    onCheckedChange = vm::setVerify
                )
            }
        }
        item {
            Button(
                onClick = { showConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RaspberryRed)
            ) {
                Text("WRITE IMAGE", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showConfirm) {
        EraseConfirmDialog(
            target = state.selectedTarget,
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                vm.startWrite()
            }
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun WarningCard(title: String, body: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(body, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun EraseConfirmDialog(
    target: StorageTarget?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Erase selected storage?") },
        text = {
            Column {
                Text(
                    "This will permanently overwrite " +
                        (target?.path ?: "the selected device") +
                        ". Type ERASE to continue."
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    label = { Text("ERASE") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = text.trim().equals("ERASE", true),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Erase and write") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun WriteScreen(state: ImagerUiState, vm: ImagerViewModel) {
    val p = state.flashProgress
    val isIndeterminate =
        p.bytesTotal == null && p.stage == FlashStage.DOWNLOADING_WRITING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .background(
                    when (p.stage) {
                        FlashStage.DONE -> RaspberryGreen.copy(alpha = 0.18f)
                        FlashStage.ERROR -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when (p.stage) {
                FlashStage.DONE -> Text("✓", fontSize = 54.sp, color = RaspberryGreen)
                FlashStage.ERROR -> Text("!", fontSize = 54.sp, color = MaterialTheme.colorScheme.error)
                else -> CircularProgressIndicator()
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(p.message.ifBlank { "Preparing" }, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            p.detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        if (isIndeterminate) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { p.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (p.bytesDone > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                ImageWriter.formatBytes(p.bytesDone) +
                    (p.bytesTotal?.let { " / " + ImageWriter.formatBytes(it) } ?: ""),
                style = MaterialTheme.typography.labelLarge
            )
        }

        state.flashError?.let {
            Spacer(Modifier.height(18.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(28.dp))
        when {
            state.writing -> OutlinedButton(onClick = vm::cancelWrite) {
                Text("Cancel")
            }
            p.stage == FlashStage.DONE -> Button(onClick = vm::resetAfterWrite) {
                Text("Done")
            }
            else -> OutlinedButton(onClick = { vm.goTo(WizardStep.REVIEW) }) {
                Text("Back")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    state: ImagerUiState,
    onDismiss: () -> Unit,
    onSetRepo: (String) -> Unit,
    onSetVerify: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    var repo by remember(state.repoUrl) { mutableStateOf(state.repoUrl) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("App options", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))

            Text("OS repository", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = repo,
                onValueChange = { repo = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSetRepo(repo) }) { Text("Apply") }
                OutlinedButton(onClick = onRefresh) { Text("Refresh") }
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Verify after writing", fontWeight = FontWeight.Bold)
                    Text("Recommended", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(state.verifyAfterWrite, onSetVerify)
            }

            Spacer(Modifier.height(24.dp))
            Text("About", fontWeight = FontWeight.Bold)
            Text(
                "Native Android implementation inspired by the open-source Raspberry Pi Imager. " +
                    "Uses Raspberry Pi's official v4 OS catalogue. Raw media writing is protected by a removable/USB safety gate.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingState(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(14.dp))
        Text(text)
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = retry) { Text("Retry") }
    }
}

private fun sourceName(source: ImageSource?): String = when (source) {
    is ImageSource.Remote -> source.item.name
    is ImageSource.Local -> source.displayName
    null -> "Not selected"
}
