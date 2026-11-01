package dev.mietze.piimager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.mietze.piimager.data.OsItem
import dev.mietze.piimager.system.ImageWriter

@Composable
fun LibrarySettings(vm: LibraryViewModel) {
 val state by vm.ui.collectAsState()
 Text("OS libraries", fontSize = 22.sp, fontWeight = FontWeight.Bold)
 SettingSwitch("Custom OS library", "Activate 10 catalogue entries. No automatic image downloads.", state.enabled, vm::activate)
 if (state.enabled) Text("${state.enabledIds.size} enabled · metadata only", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
 LibraryManagement(vm)
 LibraryDiagnostics(vm)
 HorizontalDivider(Modifier.padding(vertical = 6.dp))
}

@Composable
fun OsLibraryPane(vm: LibraryViewModel, onSettings: () -> Unit, onFlash: () -> Unit, onUseImage: (OsItem) -> Unit) {
 val state by vm.ui.collectAsState()
 var custom by rememberSaveable { mutableStateOf(false) }
 var query by rememberSaveable { mutableStateOf("") }
 var category by rememberSaveable { mutableStateOf("All") }
 var favoritesOnly by rememberSaveable { mutableStateOf(false) }
 Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
  Spacer(Modifier.height(6.dp))
  Text("OS Library", fontSize = 28.sp, fontWeight = FontWeight.Bold)
  Text("Discover first. Download only when you choose.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
   FilterChip(selected = !custom, onClick = { custom = false }, label = { Text("Raspberry OS") })
   FilterChip(selected = custom, onClick = { custom = true }, label = { Text("Custom OS") })
  }
  if (!custom) {
   Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
     Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
     Text("Raspberry Pi OS", fontSize = 24.sp, fontWeight = FontWeight.Bold)
     Text("Open the native Imager to choose a Raspberry model, OS variant, first-boot settings and target drive.")
     Button(onClick = onFlash, modifier = Modifier.fillMaxWidth()) { Text("Open Raspberry OS catalogue") }
     Text("Nothing is written until the final confirmation.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }
  } else if (!state.enabled) {
   Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(18.dp))
    Text("Custom OS Library", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Activate it in the settings", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(18.dp))
    Button(onClick = onSettings) { Text("Open settings") }
    Spacer(Modifier.height(12.dp))
    Text("10 entries ready. No ROM downloads.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
   }
  } else {
   Row(verticalAlignment = Alignment.CenterVertically) {
    Text("${state.enabledIds.size} sources enabled", Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
    TextButton(onClick = onSettings) { Text("Settings") }
   }
   OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
    placeholder = { Text("Search operating systems") }, leadingIcon = { Icon(Icons.Default.Search, null) },
    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear search") } })
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, label = { Text("Favorites") }, leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) })
    (listOf("All") + state.entries.map { it.category }.distinct()).forEach { group ->
     FilterChip(category == group, { category = group }, label = { Text(group) })
    }
   }
   val visible = state.entries.filter { entry ->
    entry.id in state.enabledIds && (!favoritesOnly || entry.id in state.favorites) &&
     (category == "All" || category == entry.category) &&
     listOf(entry.name, entry.description, entry.category).any { it.contains(query.trim(), true) }
   }
   LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (visible.isEmpty()) item { Text("No entries match these filters. Clear the search or choose All.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    items(visible, key = { it.id }) { entry ->
     LibraryEntryCard(entry, onClick = { vm.selectEntry(entry) }, favorite = entry.id in state.favorites, onFavorite = { vm.toggleFavorite(entry.id) })
    }
   }
  }
 }
 state.selected?.let { entry -> LibraryEntryDialog(entry, state, vm, onUseImage) }
}

@Composable
fun LibraryEntryCard(entry: LibraryEntry, onClick: () -> Unit, favorite: Boolean = false, onFavorite: (() -> Unit)? = null) {
 Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
  Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
   val logoColor = when (entry.id) {
    "ubuntu" -> Color(0xFFE95420)
    "dietpi" -> Color(0xFF83B735)
    "libreelec" -> Color(0xFF1B94C9)
    "osmc" -> Color(0xFF3186B0)
    "volumio" -> Color(0xFFE74E3B)
    "retropie" -> Color(0xFFE53935)
    "recalbox" -> Color(0xFF37A9E7)
    "homeassistant" -> Color(0xFF18BCF2)
    "kali" -> Color(0xFF367BF0)
    "emteria" -> Color(0xFF82B342)
    else -> MaterialTheme.colorScheme.primary
   }
   Surface(shape = RoundedCornerShape(15.dp), color = logoColor.copy(alpha = 0.18f),
     modifier = Modifier.size(58.dp)) {
    Box(contentAlignment = Alignment.Center) {
     Icon(when (entry.category) {
      "Media" -> Icons.Default.PlayCircle
      "Gaming" -> Icons.Default.SportsEsports
      "Smart home" -> Icons.Default.Home
      "Research" -> Icons.Default.Security
      "Android" -> Icons.Default.Android
      "Lightweight" -> Icons.Default.Bolt
      else -> Icons.Default.Computer
     }, contentDescription = null, tint = logoColor, modifier = Modifier.size(30.dp))
    }
   }
   Spacer(Modifier.width(14.dp))
   Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(entry.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    Text(entry.category, color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
    Text(entry.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
   }
   if (onFavorite != null) IconButton(onClick = onFavorite) {
    Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
     if (favorite) "Remove ${entry.name} from favorites" else "Add ${entry.name} to favorites",
     tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
   } else Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryEntryDialog(entry: LibraryEntry, state: LibraryState, vm: LibraryViewModel, onUseImage: (OsItem) -> Unit) {
 val context = androidx.compose.ui.platform.LocalContext.current
 var selectedImage by remember(entry.id) { mutableStateOf<OsItem?>(null) }
 var board by rememberSaveable(entry.id) { mutableStateOf("All models") }
 var imageQuery by rememberSaveable(entry.id) { mutableStateOf("") }
 val variants = state.variants.filter { image ->
  LibraryFilters.matchesBoard(image.devices, board) && (image.name + " " + image.description).contains(imageQuery.trim(), true)
 }.sortedByDescending { it.releaseDate ?: "" }
 Dialog(onDismissRequest = vm::closeEntry, properties = DialogProperties(usePlatformDefaultWidth = false)) {
  MaterialTheme(colorScheme = managerPalette(context.getSharedPreferences("pi_manager_v1", 0).getString("theme", "Raspberry Dark") ?: "Raspberry Dark")) {
   Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
     Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = vm::closeEntry) { Icon(Icons.Default.Close, "Close entry") }
      Text(entry.name, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
     }
     Text(entry.description, fontSize = 15.sp)
     Text(entry.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
     Text("Images are not downloaded while browsing.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
     FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { vm.loadVariants(state.variants.isNotEmpty()) }, enabled = !state.loading) {
       Text(if (state.variants.isEmpty()) "Load image variants" else "Refresh metadata")
      }
      OutlinedButton(onClick = {
       runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(entry.website))) }
        .onFailure { android.widget.Toast.makeText(context, "No browser available", android.widget.Toast.LENGTH_SHORT).show() }
      }) { Text("Website") }
     }
     Text(state.metadataStatus, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
     if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
     state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
     if (!state.loading && state.variants.isEmpty() && state.error == null) {
      Text("Tap Load image variants to fetch the current catalogue metadata. This does not fetch OS images.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
     if (state.variants.isNotEmpty()) {
      OutlinedTextField(imageQuery, { imageQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
       placeholder = { Text("Filter image variants") }, leadingIcon = { Icon(Icons.Default.Search, null) })
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       LibraryFilters.boards.forEach { model -> FilterChip(board == model, { board = model }, label = { Text(model) }) }
      }
      Text("${variants.size} matching images · model filter uses publisher tags", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
     LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (variants.isEmpty() && state.variants.isNotEmpty()) item { Text("No images match this model or search. No compatibility is assumed for missing publisher tags.") }
      items(variants, key = { it.url ?: it.name }) { image ->
       Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
         Text(image.name, fontWeight = FontWeight.SemiBold)
         if (image.description.isNotBlank()) Text(image.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
         Text("Boards: " + image.devices.joinToString().ifBlank { "Not specified by publisher; verify manually" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
         image.downloadSize?.let { Text("Download: ${ImageWriter.formatBytes(it)}", fontSize = 12.sp) }
         image.releaseDate?.let { Text("Publisher release: $it", fontSize = 11.sp) }
         Text(if (image.downloadSha256 != null || image.extractSha256 != null) "Publisher SHA-256 provided" else "No publisher SHA-256 supplied", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
         Button(onClick = { selectedImage = image }, modifier = Modifier.fillMaxWidth()) { Text("Use in Imager") }
        }
       }
      }
     }
    }
   }
  }
 }
 selectedImage?.let { image ->
  AlertDialog(onDismissRequest = { selectedImage = null }, title = { Text("Open in native Imager?") },
   text = { Text("${image.name}\n\nChoose the matching Raspberry model and review the image and drive before writing. No download starts now. ZIP files must contain a complete disk image, not separate firmware partitions.") },
   confirmButton = { TextButton(onClick = {
    runCatching { LibraryWire.decode(LibraryWire.encode(image)) }.onSuccess {
     selectedImage = null; vm.closeEntry(); onUseImage(it)
    }.onFailure { android.widget.Toast.makeText(context, it.message, android.widget.Toast.LENGTH_LONG).show() }
   }) { Text("Continue to Imager") } },
   dismissButton = { TextButton(onClick = { selectedImage = null }) { Text("Cancel") } })
 }
}
