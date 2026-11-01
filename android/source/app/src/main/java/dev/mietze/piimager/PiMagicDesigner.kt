package dev.mietze.piimager

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Dry-run layout designer. Never writes to disks or implies hardware emulation. */
internal data class MagicBlock(val id:String,val name:String,val kind:String,val sizeGb:Int,val dependency:String="")
private val raspberryRed=Color(0xFFE94173)
private val catalog = listOf(
    "Raspberry Pi OS" to "OS", "Ubuntu" to "OS", "DietPi" to "OS",
    "Home Assistant OS" to "OS", "LibreELEC" to "OS", "Kali Linux" to "OS",
    "Boot acceptable patch file" to "PATCH", "EEPROM boot config" to "BOOT",
    "Boot A" to "BOOT", "Boot B" to "BOOT", "Kernel / initramfs" to "BOOT",
    "DTB overlay" to "DRIVER", "USB driver" to "DRIVER",
    "Wi-Fi driver" to "DRIVER", "Service partition" to "SERVICE",
    "Recovery image (optional)" to "RECOVERY", "Free Space" to "FREE"
)
internal fun decodeMagicBlocks(raw:String):List<MagicBlock> = runCatching {
    val arr=JSONArray(raw)
    (0 until arr.length()).map { i-> arr.getJSONObject(i).let {
        MagicBlock(it.getString("id"),it.getString("name"),it.getString("kind"),it.getInt("gb"),it.optString("dependency"))
    }}.take(40)
}.getOrDefault(emptyList())
internal fun encodeMagicBlocks(blocks:List<MagicBlock>):String = JSONArray().apply {
    blocks.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("kind",it.kind).put("gb",it.sizeGb).put("dependency",it.dependency)) }
}.toString()
/** Removing any block, including recovery, is allowed; incoming links are cleared. */
internal fun removeMagicBlock(blocks:List<MagicBlock>, id:String):List<MagicBlock> =
    blocks.filterNot { it.id == id }.map { if (it.dependency == id) it.copy(dependency="") else it }

private fun diagnostics(blocks:List<MagicBlock>,capacity:Int):List<String> {
    val messages=mutableListOf<String>()
    if(blocks.isEmpty()) messages+="Add a boot component to start the layout."
    if(blocks.none{it.kind=="OS"}) messages+="No operating system block configured."
    val allocated=blocks.sumOf{it.sizeGb}
    if(allocated>capacity) messages+="ERROR: allocated ${allocated} GB exceeds card capacity ${capacity} GB."
    val ids=blocks.map{it.id}.toSet()
    blocks.filter { it.dependency.isNotEmpty() && it.dependency !in ids }.forEach {
        messages+="ERROR: ${it.name} references a missing component."
    }
    val byId=blocks.associateBy{it.id}
    blocks.forEach { block ->
        val seen=mutableSetOf<String>();var cursor=block
        while(cursor.dependency.isNotEmpty()){
            if(!seen.add(cursor.id)){ messages+="ERROR: circular boot dependency near ${block.name}.";break}
            cursor=byId[cursor.dependency]?:break
        }
    }
    if(blocks.count{it.kind=="OS"}>1 && blocks.none{it.kind=="BOOT"}) messages+="Multi-OS design needs a verified boot selector, not just adjacent partitions."
    if(messages.isEmpty()) messages+="Layout passes basic allocation and dependency checks."
    messages+="EmulateLoad: dependency dry-run only. No real Raspberry Pi firmware boot or QEMU execution."
    messages+="Physical Pi compatibility and device drivers remain unverified."
    return messages.distinct()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PiMagicDesigner(){
    val ctx=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember {ctx.getSharedPreferences("pi_magic_designer",0)}
    var blocks by remember { mutableStateOf(decodeMagicBlocks(prefs.getString("layout", "[]") ?: "[]")) }
    var capacity by rememberSaveable {mutableIntStateOf(prefs.getInt("capacity",64))}
    var picker by remember {mutableStateOf(false)}
    var report by remember {mutableStateOf(false)}
    var readyChoices by remember {mutableStateOf(false)}
    var linking by remember {mutableStateOf<String?>(null)}
    val allocated=blocks.sumOf{it.sizeGb}
    fun save(next:List<MagicBlock>){blocks=next;prefs.edit().putString("layout",encodeMagicBlocks(next)).apply()}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=14.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Default.AccountTree,null,tint=raspberryRed,modifier=Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Pi Magic Image Designer",fontSize=21.sp,fontWeight=FontWeight.Bold)
                Text("Stack Builder · non-destructive layout",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            dev.mietze.piimager.workbench.WorkbenchEntryButton()
        }
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically){
            Text("Card",fontSize=12.sp)
            listOf(16,32,64,128,256).forEach { size->
                FilterChip(selected=capacity==size,onClick={capacity=size;prefs.edit().putInt("capacity",size).apply()},
                    label={Text("${size}G",fontSize=10.sp)})
            }
        }
        LinearProgressIndicator(progress={ (allocated.toFloat()/capacity).coerceIn(0f,1f) },
            modifier=Modifier.fillMaxWidth().height(7.dp),color=if(allocated>capacity) Color.Red else raspberryRed)
        Row(Modifier.fillMaxWidth().padding(vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween){
            Text("${allocated} GB allocated",fontSize=12.sp)
            Text("${(capacity-allocated).coerceAtLeast(0)} GB unreserved vacuum",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("BOOT STACK",fontSize=12.sp,fontWeight=FontWeight.Bold,color=raspberryRed)
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(top=12.dp,bottom=14.dp)){
            itemsIndexed(blocks,key={_,b->b.id}) { index, block ->
                Surface(shape=RoundedCornerShape(17.dp),color=MaterialTheme.colorScheme.surface,
                    border=BorderStroke(1.dp,raspberryRed.copy(alpha=0.25f))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Surface(shape=RoundedCornerShape(9.dp),color=raspberryRed.copy(alpha=0.18f)){
                                Icon(when(block.kind){"OS"->Icons.Default.Computer;"BOOT"->Icons.Default.PowerSettingsNew;"DRIVER"->Icons.Default.Extension;"FREE"->Icons.Default.SdStorage;else->Icons.Default.Build},
                                    null,Modifier.padding(9.dp),tint=raspberryRed)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)){
                                Text(block.name,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                                Text("${block.kind} · ${block.sizeGb} GB",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick={save(removeMagicBlock(blocks,block.id))}) {
                                Icon(Icons.Default.Close,"Remove block",tint=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if(block.dependency.isNotEmpty()){
                            val dependsOn=blocks.find{it.id==block.dependency}?.name ?: "Missing"
                            Text("↳ Requires: $dependsOn",color=raspberryRed,fontSize=11.sp)
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically){
                            TextButton(onClick={if(index>0)save(blocks.toMutableList().apply{add(index-1,removeAt(index))})},enabled=index>0){Text("↑ Move")}
                            TextButton(onClick={if(index<blocks.lastIndex)save(blocks.toMutableList().apply{add(index+1,removeAt(index))})},enabled=index<blocks.lastIndex){Text("↓ Move")}
                            TextButton(onClick={linking=block.id}){Text("Connect")}
                            TextButton(onClick={save(blocks.map {if(it.id==block.id)it.copy(sizeGb=when(it.sizeGb){0->1;1->2;2->4;4->8;8->16;else->0}) else it})}){Text("Resize")}
                        }
                    }
                }
            }
            item{
                OutlinedButton(onClick={picker=true},modifier=Modifier.fillMaxWidth().height(60.dp),
                    shape=RoundedCornerShape(18.dp)){
                    Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Add stack block")
                }
            }
        }
        Button(onClick={readyChoices=true},modifier=Modifier.fillMaxWidth().padding(bottom=12.dp).heightIn(min=54.dp),
            shape=RoundedCornerShape(22.dp)) {
            Icon(Icons.Default.TaskAlt,null);Spacer(Modifier.width(10.dp))
            Text("Ready · Continue",fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.weight(1f));Icon(Icons.Default.ChevronRight,null)
        }
    }
    if(readyChoices) {
        val findings=diagnostics(blocks,capacity)
        val faults=findings.filter{it.startsWith("ERROR:")}
        AlertDialog(
            onDismissRequest={readyChoices=false},
            icon={Icon(Icons.Default.TaskAlt,null,tint=raspberryRed)},
            title={Text(if(faults.isEmpty()&&blocks.any{it.kind=="OS"}) "Ready to continue" else "Review your image layout")},
            text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("${blocks.size} stack blocks · $allocated / $capacity GB reserved",fontWeight=FontWeight.Medium)
                Text(if(faults.isEmpty())"Choose a validation run or open the real device flashing wizard."
                    else "${faults.size} layout issue(s). Check the report before using this as a boot design.",
                    color=if(faults.isEmpty())MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                if(faults.isNotEmpty())faults.forEach{Text("• $it",fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
                HorizontalDivider()
                Text("EMULATELOAD is a design/dependency simulation, not a real Raspberry Pi boot.",
                    fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Flash on Device opens the native Raspberry Pi Imager. It does not write this draft stack: choose a real compatible image and disk in the wizard, then explicitly confirm erasure.",
                    fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }},
            confirmButton={Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={readyChoices=false;report=true},modifier=Modifier.fillMaxWidth(),
                    shape=RoundedCornerShape(14.dp)){
                    Icon(Icons.Default.FactCheck,null);Spacer(Modifier.width(9.dp));Text("EmulateLoad · Validate design")
                }
                OutlinedButton(onClick={
                    readyChoices=false
                    ctx.startActivity(android.content.Intent(ctx,ImagerActivity::class.java))
                },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                    Icon(Icons.Default.Usb,null);Spacer(Modifier.width(9.dp));Text("Flash on Device · Open Imager")
                }
            }},
            dismissButton={TextButton(onClick={readyChoices=false}){Text("Back to Builder")}}
        )
    }
    if(picker) AlertDialog(onDismissRequest={picker=false},
        title={Text("Add boot / storage block")},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
            Text("Recovery is optional. Standard Raspberry Pi OS images use boot and root partitions, not an extra recovery partition. You can add or remove a recovery block yourself.",
                fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=12.dp))
            catalog.forEach { (name,kind) ->
                TextButton(onClick={
                    if(blocks.size<40) save(blocks+MagicBlock(UUID.randomUUID().toString(),name,kind,
                        if(kind=="FREE" || kind=="PATCH" || kind=="DRIVER" || kind=="BOOT") 0 else 8))
                    picker=false
                },modifier=Modifier.fillMaxWidth()) {Text("$name  ·  $kind")}
            }
        }},confirmButton={TextButton(onClick={picker=false}){Text("Close")}})
    linking?.let { source ->
        AlertDialog(onDismissRequest={linking=null},title={Text("Connect boot dependency")},
            text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){
                Text("Choose the component required before this block.",fontSize=12.sp)
                TextButton(onClick={save(blocks.map{if(it.id==source)it.copy(dependency="") else it});linking=null}){Text("No dependency")}
                blocks.filter{it.id!=source}.forEach { other ->
                    TextButton(onClick={save(blocks.map{if(it.id==source)it.copy(dependency=other.id) else it});linking=null}) {
                        Text(other.name)
                    }
                }
            }},confirmButton={TextButton(onClick={linking=null}){Text("Cancel")}})
    }
    if(report){
        val notes=diagnostics(blocks,capacity)
        AlertDialog(onDismissRequest={report=false},title={Text("EmulateLoad · diagnostic report")},
            text={Column(Modifier.heightIn(max=410.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
                notes.forEach {Text("• $it",fontSize=13.sp)}
            }},confirmButton={TextButton(onClick={report=false}){Text("Done")}})
    }
}
