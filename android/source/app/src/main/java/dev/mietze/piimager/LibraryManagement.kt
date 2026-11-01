package dev.mietze.piimager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryManagement(vm: LibraryViewModel) {
 val state by vm.ui.collectAsState()
 val context = LocalContext.current
 val scope = rememberCoroutineScope()
 var managing by remember { mutableStateOf(false) }
 var adding by remember { mutableStateOf(false) }
 var busy by remember { mutableStateOf(false) }
 var notice by remember { mutableStateOf("") }
 var pending by remember { mutableStateOf<LibraryImportPlan?>(null) }
 val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
  if (uri != null) scope.launch {
   busy = true
   notice = runCatching {
    val text = vm.exportMetadata()
    withContext(Dispatchers.IO) {
     val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot write the selected file")
     output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
    "Library metadata saved. No OS images or SSH credentials included."
   }.getOrElse { it.message ?: "Export failed" }
   busy = false
  }
 }
 val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if (uri != null) scope.launch {
   busy = true
   runCatching {
    withContext(Dispatchers.IO) {
     val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read the selected file")
     input.use { LibraryBackup.parse(LibraryBackup.readLimited(it)) }
    }
   }.onSuccess { pending = it; notice = "Backup validated; confirm to merge." }
    .onFailure { notice = it.message ?: "Import failed" }
   busy = false
  }
 }
 FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
  OutlinedButton(onClick = { managing = true }, enabled = state.enabled && !busy) { Text("Manage sources") }
  OutlinedButton(onClick = { adding = true }, enabled = state.enabled && !busy) { Text("Add image link") }
  TextButton(onClick = { export.launch("RaspberryManager-Library.json") }, enabled = !busy) { Text("Export metadata") }
  TextButton(onClick = { importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy) { Text("Import metadata") }
 }
 if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
 if (notice.isNotBlank()) Text(notice, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
 if (managing) ManageSourcesDialog(state, vm) { managing = false }
 if (adding) AddSourceDialog(vm) { adding = false }
 pending?.let { plan ->
  AlertDialog(onDismissRequest = { pending = null }, title = { Text("Merge library metadata?") },
   text = { Text("${plan.entries.size} custom image links, ${plan.enabledIds.size} enabled source IDs and ${plan.favorites.size} favorites. Existing sources remain. This does not activate a disabled library, download images, change device profiles or write storage.") },
   confirmButton = { TextButton(onClick = {
    notice = runCatching { vm.mergeImport(plan); "Library metadata merged." }.getOrElse { it.message ?: "Import failed" }
    pending = null
   }) { Text("Merge") } },
   dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } })
 }
}

@Composable
fun AddSourceDialog(vm: LibraryViewModel, onClose: () -> Unit) {
 var title by remember { mutableStateOf("") }
 var url by remember { mutableStateOf("") }
 var hash by remember { mutableStateOf("") }
 var error by remember { mutableStateOf("") }
 AlertDialog(onDismissRequest = onClose, title = { Text("Add a custom image link") },
  text = {
   Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Only a link is saved. No automatic download.", fontSize = 13.sp)
    OutlinedTextField(title, { title = it.take(80) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(url, { url = it.take(4096) }, label = { Text("Direct HTTPS image URL") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
    OutlinedTextField(hash, { hash = it.take(64) }, label = { Text("Download SHA-256 (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
    Text("The hash describes the downloaded file, including compression. Only use full disk images. Model support must be checked manually.", fontSize = 11.sp)
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
   }
  },
  confirmButton = { TextButton(onClick = {
   runCatching { vm.addCustom(title, url, hash) }.onSuccess { onClose() }.onFailure { error = it.message ?: "Invalid image link" }
  }) { Text("Save link") } },
  dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
fun ManageSourcesDialog(state: LibraryState, vm: LibraryViewModel, onClose: () -> Unit) {
 var removing by remember { mutableStateOf<LibraryEntry?>(null) }
 Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
   Column(Modifier.fillMaxSize().safeDrawingPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
     IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close source manager") }
     Text("Library sources", fontSize = 23.sp, fontWeight = FontWeight.Bold)
    }
    Text("Toggle visibility, not downloads. Your choices survive app updates.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
     items(state.entries, key = { it.id }) { entry ->
      Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
       Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
         Text(entry.name, fontWeight = FontWeight.SemiBold)
         Text(if (entry.imageUrl == null) "Prepared catalogue source" else "Your custom image link", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (entry.imageUrl != null) IconButton(onClick = { removing = entry }) { Icon(Icons.Default.DeleteOutline, "Remove ${entry.name}") }
        Switch(checked = entry.id in state.enabledIds, onCheckedChange = { vm.setSourceEnabled(entry.id, it) })
       }
      }
     }
    }
   }
  }
 }
 removing?.let { entry ->
  AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove this image link?") },
   text = { Text("Only ${entry.name}'s saved link and favorite flag are removed. No files or Raspberry Pi data are deleted.") },
   confirmButton = { TextButton(onClick = { vm.removeCustom(entry.id); removing = null }) { Text("Remove link") } },
   dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } })
 }
}
