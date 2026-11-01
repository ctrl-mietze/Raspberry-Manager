package dev.mietze.piimager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceLibrarySummary(deviceCount: Int, vm: LibraryViewModel, openLibrary: () -> Unit) {
 val state by vm.ui.collectAsState()
 Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
  Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
   Text("Workspace at a glance", fontWeight = FontWeight.Bold, fontSize = 19.sp)
   FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf("$deviceCount saved devices", "${if (state.enabled) state.enabledIds.size else 0} active OS sources", "${state.favorites.size} favorites").forEach { text ->
     Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
      Text(text, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
     }
    }
   }
   Text(if (state.enabled) "Your library is active. Browsing does not download operating systems." else "The custom library is off. Activate it in Settings to enable ten prepared sources.",
    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
   TextButton(onClick = openLibrary) { Text("Open OS Library") }
   if (state.enabled && state.recentIds.any { it in state.enabledIds }) {
    Text("Recently opened", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     state.recentIds.mapNotNull { id -> state.entries.find { it.id == id && id in state.enabledIds } }.forEach { entry ->
      AssistChip(onClick = { vm.selectEntry(entry); openLibrary() }, label = { Text(entry.name) },
       leadingIcon = { Icon(Icons.Default.History, null, Modifier.size(16.dp)) })
     }
    }
   }
  }
 }
}

private data class ReleaseNote(val label: String, val title: String, val detail: String)
private val notes = listOf(
 ReleaseNote("V2", "Raspberry startup", "Native Raspberry boot logo, system splash and a skippable intro. Saved toggles for the animation and startup chooser."),
 ReleaseNote("V3", "Second native OS library", "An optional custom library with ten prepared metadata sources, explicit variant loading and native Imager handoff."),
 ReleaseNote("V5", "Discover and organize", "Search, categories, favorites, image-variant filters and publisher-tag-based Raspberry model selection."),
 ReleaseNote("V7", "Your own image sources", "Enable individual sources, save custom HTTPS image links and optional SHA-256 hashes. Export or merge metadata backups without OS images or credentials."),
 ReleaseNote("VX", "Complete Raspberry workspace", "Recent-source shortcuts, workspace statistics, responsive library layouts, this release center, bounded and validated catalogue caching, and library diagnostics.")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReleaseCenter() {
 var open by remember { mutableStateOf(false) }
 val context = LocalContext.current
 val report = "Raspberry Manager ${ReleaseInfo.LABEL} (${ReleaseInfo.VERSION})\nPackage: ${context.packageName}\nNative Android. OS library downloads are user-initiated."
 Card(onClick = { open = true }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))) {
  Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
   Icon(Icons.Default.NewReleases, null, tint = MaterialTheme.colorScheme.primary)
   Spacer(Modifier.width(12.dp))
   Column(Modifier.weight(1f)) {
    Text("Raspberry Manager ${ReleaseInfo.LABEL}", fontWeight = FontWeight.Bold)
    Text("Version ${ReleaseInfo.VERSION} · See what's new", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
   Icon(Icons.Default.ChevronRight, null)
  }
 }
 if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
   Column(Modifier.fillMaxSize().safeDrawingPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
     IconButton(onClick = { open = false }) { Icon(Icons.Default.Close, "Close release center") }
     Text("Raspberry Manager VX", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
    Text("Five cumulative milestones. One Raspberry workspace.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
     items(notes, key = { it.label }) { note ->
      Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
       Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(note.label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
        Text(note.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(note.detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
       }
      }
     }
     item {
      Text("Independent community app by Veyra. Raspberry Pi and the listed operating-system brands belong to their respective owners. Raspberry Pi Connect opens the official service. No OS image or game ROM is bundled in this app.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     OutlinedButton(onClick = {
      val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
      clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Raspberry Manager version", report))
      android.widget.Toast.makeText(context, "Version details copied", android.widget.Toast.LENGTH_SHORT).show()
     }) { Text("Copy version info") }
     TextButton(onClick = { open = false }) { Text("Back to settings") }
    }
   }
  }
 }
}

@Composable
fun LibraryDiagnostics(vm: LibraryViewModel) {
 val state by vm.ui.collectAsState()
 var confirmClear by remember { mutableStateOf(false) }
 Text("Catalogue metadata", fontWeight = FontWeight.SemiBold)
 Text(state.metadataStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
 Text("Clearing this cache removes only catalogue metadata. Your sources, favorites, SSH profiles and image files remain.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
 TextButton(onClick = { confirmClear = true }, enabled = !state.loading) { Text("Clear catalogue cache") }
 if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear catalogue metadata?") },
  text = { Text("No OS images or saved settings are deleted. The next Load image variants action will request fresh metadata.") },
  confirmButton = { TextButton(onClick = { vm.clearMetadataCache(); confirmClear = false }) { Text("Clear metadata") } },
  dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}
