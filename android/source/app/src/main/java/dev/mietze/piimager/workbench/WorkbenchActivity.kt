package dev.mietze.piimager.workbench

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.mietze.piimager.LibraryViewModel

/** Separate editor; never opens block devices or modifies the normal Image Builder's preferences. */
class WorkbenchActivity:ComponentActivity(){
    private val vm:WorkbenchVm by viewModels()
    private val library:LibraryViewModel by viewModels()
    private var pendingAttach=""
    private var includeAttachments=false
    private val importer=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(vm::importProject)}
    private val attachment=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null&&pendingAttach.isNotBlank())vm.attach(pendingAttach,uri);pendingAttach=""}
    private val zipExport=registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->uri?.let{vm.export(it,"zip",includeAttachments)}}
    private val jsonExport=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->uri?.let{vm.export(it,"json")}}
    private val svgExport=registerForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")){uri->uri?.let{vm.export(it,"svg")}}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        pendingAttach=savedInstanceState?.getString("attach").orEmpty();includeAttachments=savedInstanceState?.getBoolean("files")?:false
        setContent{
            val state by library.ui.collectAsState()
            val repos=if(state.enabled)state.entries.filter{it.id in state.enabledIds}.map{WbRepoEntry(it.name,it.description,it.website)}else emptyList()
            MaterialTheme(colorScheme=darkColorScheme(primary=Berry,onPrimary=Color.White,background=WbBackground,surface=Color(0xFF21151E),surfaceVariant=Color(0xFF30222C),onSurface=Color(0xFFF8EBF1),onSurfaceVariant=Color(0xFFCBB9C3),secondary=Color(0xFFB4CC89),error=Color(0xFFFFB4AB))){
                WorkbenchScreen(vm,repos,onBack={finish()},onImport={importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))},
                    onExport={type,files->includeAttachments=files;val name="Raspberry-Workbench-${vm.project.id.take(8)}";when(type){"zip"->zipExport.launch("$name.zip");"svg"->svgExport.launch("$name.svg");else->jsonExport.launch("$name.rwb.json")}},
                    onAttach={id->pendingAttach=id;attachment.launch(arrayOf("*/*"))},
                    onProvider={url->if(WbCodec.safeHttps(url))runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}})
            }
        }
    }
    override fun onSaveInstanceState(outState:Bundle){outState.putString("attach",pendingAttach);outState.putBoolean("files",includeAttachments);super.onSaveInstanceState(outState)}
}
@Composable internal fun WorkbenchEntryButton(){
    val context=LocalContext.current
    FilledTonalIconButton(onClick={context.startActivity(Intent(context,WorkbenchActivity::class.java))},
        colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=Color(0xFF771B3C),contentColor=Color(0xFFFFD6E6)),
        shape=RoundedCornerShape(16.dp),modifier=Modifier.size(48.dp)){
        Icon(Icons.Default.BusinessCenter,"Open Pi Magic Workbench",Modifier.size(25.dp))
    }
}
