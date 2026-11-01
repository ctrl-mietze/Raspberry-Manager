package dev.mietze.piimager.workbench

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable internal fun WbDialog(title:String,onClose:()->Unit,content:@Composable ColumnScope.()->Unit){
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Box(Modifier.fillMaxSize().safeDrawingPadding().background(Brush.verticalGradient(listOf(Color(0xFF160D14),WbBackground,WbBackground)))) {
            Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){
                Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick=onClose){Icon(Icons.Default.ArrowBack,"Back")}
                    Text(title,Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=21.sp)
                }
                content()
            }
        }
    }
}
@Composable internal fun ChoiceRow(label:String,value:String,options:List<String>,onChange:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
        Column(Modifier.weight(1f)){Text(label,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value.ifBlank{"Choose…"},fontSize=14.sp)}
        Icon(Icons.Default.ExpandMore,null)
    }
    if(open)AlertDialog(onDismissRequest={open=false},title={Text(label)},text={LazyColumn(Modifier.heightIn(max=420.dp)){items(options){item->TextButton(onClick={onChange(item);open=false},modifier=Modifier.fillMaxWidth()){Text(item)}}}},confirmButton={TextButton(onClick={open=false}){Text("Cancel")}})
}
@Composable internal fun WbSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit,detail:String=""){
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(label,fontSize=14.sp);if(detail.isNotEmpty())Text(detail,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked,onChange)}
}
@Composable internal fun WbHint(text:String){Surface(color=Berry.copy(alpha=.1f),shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){Text(text,Modifier.padding(14.dp),fontSize=12.sp,color=Color(0xFFE2BECF))}}

internal data class WbRepoEntry(val name:String,val description:String,val website:String)

@Composable private fun LibraryBlockCard(k:WbKind,onClick:()->Unit,depth:Int=0) {
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth().padding(start=(depth*18).dp),shape=RoundedCornerShape(20.dp),
        colors=CardDefaults.cardColors(containerColor=Color(0xFF1D151C)),border=BorderStroke(1.dp,categoryColor(k.category).copy(alpha=.20f))) {
        Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
            WbKindArt(k.id,k.category); Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(k.title,fontWeight=FontWeight.SemiBold,fontSize=15.sp)
                Text(k.description,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
                Text("${k.fields.size} settings · ${k.exportMode}",fontSize=10.sp,color=categoryColor(k.category))
            }
            Surface(shape=RoundedCornerShape(12.dp),color=Berry.copy(alpha=.13f)) { Icon(Icons.Default.Add,"Add ${k.title}",Modifier.padding(9.dp).size(18.dp),tint=Berry) }
        }
    }
}

@Composable private fun MinecraftTree(onChoose:(String)->Unit,query:String="") {
    var rootOpen by rememberSaveable { mutableStateOf(true) }
    var bedrockOpen by rememberSaveable { mutableStateOf(false) }
    var javaOpen by rememberSaveable { mutableStateOf(false) }
    val searching=query.isNotBlank(); val q=query.trim()
    val allLabels=listOf("Minecraft","Minecraft Bedrock","Nukkit","Bedrock","Minecraft Java","Bukkit","Java","Mod Client Forge","Mod Client Fabric")
    if(searching && allLabels.none{it.contains(q,true)}) return
    Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF171817)),border=BorderStroke(1.dp,Color(0xFF88B96B).copy(alpha=.38f))) {
        Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth().clickable{rootOpen=!rootOpen}.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                MinecraftCubeArt(Modifier.size(52.dp)); Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) { Text("Minecraft",fontWeight=FontWeight.Black,fontSize=19.sp); Text("Bedrock · Java · servers · mod clients",fontSize=11.sp,color=Color(0xFFAAC49B)) }
                Icon(if(rootOpen||searching)Icons.Default.ExpandLess else Icons.Default.ExpandMore,"Expand Minecraft")
            }
            if(rootOpen||searching) {
                Surface(shape=RoundedCornerShape(18.dp),color=Color(0xFF201C1B),modifier=Modifier.fillMaxWidth().padding(start=12.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            MinecraftFamilyArt(false); Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f).clickable{onChoose("mc.bedrock")}.padding(vertical=8.dp)) { Text("Minecraft Bedrock",fontWeight=FontWeight.Bold); Text("Selectable · official profile",fontSize=10.sp,color=Color(0xFFB6AD9D)) }
                            IconButton(onClick={bedrockOpen=!bedrockOpen}){Icon(if(bedrockOpen||searching)Icons.Default.ExpandLess else Icons.Default.ExpandMore,"Bedrock children")}
                        }
                        if(bedrockOpen||searching) { listOf("mc.nukkit","mc.bedrock").forEach { id -> val k=WbCatalog.kind(id); if(!searching || (k.title+" "+k.description).contains(q,true)) LibraryBlockCard(k,{onChoose(id)},2) }; Spacer(Modifier.height(6.dp)) }
                    }
                }
                Surface(shape=RoundedCornerShape(18.dp),color=Color(0xFF21191B),modifier=Modifier.fillMaxWidth().padding(start=12.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            MinecraftFamilyArt(true); Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f).clickable{onChoose("mc.java")}.padding(vertical=8.dp)) { Text("Minecraft Java",fontWeight=FontWeight.Bold); Text("Selectable · vanilla Java profile",fontSize=10.sp,color=Color(0xFFC9AEA4)) }
                            IconButton(onClick={javaOpen=!javaOpen}){Icon(if(javaOpen||searching)Icons.Default.ExpandLess else Icons.Default.ExpandMore,"Java children")}
                        }
                        if(javaOpen||searching) { listOf("mc.bukkit","mc.java","mc.mod.forge","mc.mod.fabric").forEach { id -> val k=WbCatalog.kind(id); if(!searching || (k.title+" "+k.description).contains(q,true)) LibraryBlockCard(k,{onChoose(id)},2) }; Spacer(Modifier.height(6.dp)) }
                    }
                }
            }
        }
    }
}

@Composable internal fun BlockLibrary(initialCategory:String,repos:List<WbRepoEntry>,add:(String)->Unit,addRepo:(WbRepoEntry)->Unit,onClose:()->Unit){
    var category by rememberSaveable{mutableStateOf(initialCategory)}; var search by rememberSaveable{mutableStateOf("")}
    WbDialog("Add to your workbench",onClose){
        WbHint("Choose a block visually. Minecraft lives in the middle of the catalogue as an expandable tree; adding a block never downloads or starts software.")
        Spacer(Modifier.height(8.dp)); OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Search blocks, tools, services")},leadingIcon={Icon(Icons.Default.Search,null)})
        val categories=listOf("All")+wbCategoryOrder+(if(repos.isNotEmpty())listOf("My OS library")else emptyList())
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){categories.forEach{c->FilterChip(category==c,{category=c},label={Text(c)})}}
        val q=search.trim()
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(top=6.dp,bottom=26.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
            if(category=="My OS library") items(repos.filter{q.isBlank()||(it.name+" "+it.description).contains(q,true)}){r->Card(onClick={addRepo(r)},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF1D151C))){Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically){WbKindArt("os.custom","Operating systems");Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Text(r.name,fontWeight=FontWeight.Bold);Text(r.description,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("Enabled library source · metadata only",fontSize=10.sp,color=Berry)};Icon(Icons.Default.Add,null,tint=Berry)}}}
            else {
                val groups=if(category=="All") wbCategoryOrder else listOf(category); var anything=false
                groups.forEach { group ->
                    val kinds=WbCatalog.kinds.filter{it.category==group && it.id!="minecraft" && !it.id.startsWith("mc.")}.filter{q.isBlank()||(it.title+" "+it.description+" "+it.category).contains(q,true)}
                    val mcMatches=group=="Minecraft" && (q.isBlank() || listOf("minecraft","bedrock","nukkit","java","bukkit","forge","fabric","mod client").any{it.contains(q,true)||q.contains(it,true)})
                    if(kinds.isNotEmpty()||mcMatches){anything=true;item("header-$group"){Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically){Text(group.uppercase(),fontSize=10.sp,fontWeight=FontWeight.Bold,color=categoryColor(group),modifier=Modifier.weight(1f));Text(if(group=="Minecraft")"expandable" else "${kinds.size} blocks",fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}};if(group=="Minecraft")item("minecraft-tree"){MinecraftTree(add,q)}else items(kinds,key={it.id}){k->LibraryBlockCard(k,{add(k.id)})}}
                }
                if(!anything)item{Text("No matching blocks.",Modifier.padding(20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
    }
}

@Composable internal fun MinecraftChoice(onChoose:(String)->Unit,onClose:()->Unit){
    WbDialog("Minecraft",onClose){
        WbHint("Minecraft is organised as a tree. Tap Minecraft Bedrock or Minecraft Java to select the default profile, or expand the arrow for Nukkit, Bukkit, vanilla and mod-client profiles.")
        Spacer(Modifier.height(12.dp)); Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { MinecraftTree(onChoose); Spacer(Modifier.height(18.dp)); Text("Provider downloads are opened explicitly from the inspector. Nothing is downloaded merely by choosing a block.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable internal fun NodeInspector(vm:WorkbenchVm,n:WbNode,modifier:Modifier=Modifier,onAttach:(String)->Unit,onMinecraft:(String)->Unit,onConnect:(String)->Unit,onProvider:(String)->Unit){
    var name by remember(n.id,n.name){mutableStateOf(n.name)};var notes by remember(n.id,n.notes){mutableStateOf(n.notes)}
    var group by remember(n.id,n.group){mutableStateOf(n.group)};var params by remember(n.id,n.params){mutableStateOf(n.params)}
    var priority by remember(n.id,n.priority){mutableStateOf(n.priority.toString())}
    var enabled by remember(n.id,n.enabled){mutableStateOf(n.enabled)};var optional by remember(n.id,n.optional){mutableStateOf(n.optional)};var locked by remember(n.id,n.locked){mutableStateOf(n.locked)}
    var errors by remember(n.id){mutableStateOf(emptyList<String>())};var conditions by remember{mutableStateOf(false)}
    var query by remember(n.id){mutableStateOf("")}
    val k=WbCatalog.kind(n.type)
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("BLOCK INSPECTOR",fontSize=11.sp,color=Berry,fontWeight=FontWeight.Bold)
        Text(k.title,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(k.description,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("x ${n.x.toInt()} · y ${n.y.toInt()} · ${k.exportMode}",fontSize=11.sp,color=categoryColor(k.category))
        if(n.type=="minecraft"||n.type.startsWith("mc."))Button(onClick={onMinecraft(n.id)},modifier=Modifier.fillMaxWidth()){Text("Choose Java / Bedrock")}
        OutlinedTextField(name,{name=it.take(100)},Modifier.fillMaxWidth(),label={Text("Block name")},singleLine=true)
        WbSwitch("Enabled",enabled,{enabled=it});WbSwitch("Optional",optional,{optional=it},"Marks intent; a required dependency still cannot be missing.");WbSwitch("Lock position",locked,{locked=it})
        OutlinedTextField(group,{group=it.take(80)},Modifier.fillMaxWidth(),label={Text("Group name")},singleLine=true)
        OutlinedTextField(priority,{priority=it.take(3)},Modifier.fillMaxWidth(),label={Text("Scheduling priority · 0–999")},singleLine=true)
        OutlinedTextField(notes,{notes=it.take(4096)},Modifier.fillMaxWidth(),label={Text("Notes")},minLines=2,maxLines=6)
        HorizontalDivider()
        if(k.fields.isNotEmpty())OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Find a setting")},singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)})
        k.fields.filter{(it.label+it.key).contains(query,true)}.forEach{f->val v=params[f.key]?:f.default
            when(f.type){
                WbFieldType.SWITCH->WbSwitch(f.label,v=="true",{params=params+(f.key to it.toString())},f.help)
                WbFieldType.CHOICE->{ChoiceRow(f.label,v,f.options){params=params+(f.key to it)};if(f.help.isNotBlank())Text(f.help,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                else->{OutlinedTextField(v,{params=params+(f.key to it.take(2048))},Modifier.fillMaxWidth(),label={Text(f.label)},singleLine=true);if(f.help.isNotBlank())Text(f.help,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
        errors.forEach{Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
        Button(onClick={
            val updated=n.copy(name=name.ifBlank{k.title},params=params,notes=notes,group=group,enabled=enabled,optional=optional,locked=locked,priority=priority.toIntOrNull()?:-1)
            errors=WbCatalog.fieldErrors(updated)+(if(updated.priority !in 0..999)listOf("Priority must be 0–999.")else emptyList())
            if(errors.isEmpty())vm.update(updated)
        },modifier=Modifier.fillMaxWidth()){Text("Apply block settings")}
        HorizontalDivider()
        OutlinedButton(onClick={conditions=true},modifier=Modifier.fillMaxWidth()){Text("Conditions · ${n.conditions.size}")}
        OutlinedButton(onClick={onConnect(n.id)},modifier=Modifier.fillMaxWidth()){Text("Connect / dependencies")}
        if(n.type in listOf("mc.java","mc.bukkit","mc.nukkit","mc.mod.forge","mc.mod.fabric"))OutlinedButton(onClick={vm.addRuntimeFor(n)},modifier=Modifier.fillMaxWidth()){Text("Add & connect Java runtime")}
        OutlinedButton(onClick={onAttach(n.id)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AttachFile,null);Text("Attach local file")}
        n.attachment?.let{a->Text("${a.name}\n${a.bytes} bytes · ${a.format}\nSHA-256: ${a.sha256}",fontSize=10.sp);TextButton(onClick={vm.update(n.copy(attachment=null))}){Text("Remove file reference")}}
        if(k.website.isNotBlank())TextButton(onClick={onProvider(k.website)}){Text("Open official provider")}
        if(n.type=="mc.nukkit")TextButton(onClick={onProvider("https://github.com/CloudburstMC/Nukkit")}){Text("Cloudburst Nukkit source")}
        if(n.type in listOf("mc.java","mc.bukkit"))TextButton(onClick={onProvider("https://www.minecraft.net/eula")}){Text("Read Minecraft EULA")}
        pLinkRows(vm,n.id)
        TextButton(onClick={vm.selectOnly(n.id);vm.duplicate()}){Text("Duplicate block")}
        TextButton(onClick={vm.selectOnly(n.id);vm.remove()}){Text("Delete block",color=MaterialTheme.colorScheme.error)}
        Spacer(Modifier.height(32.dp))
    }
    if(conditions)ConditionEditor(vm,n,{conditions=false})
}
@Composable private fun pLinkRows(vm:WorkbenchVm,id:String){
    val edges=vm.project.links.filter{it.from==id||it.to==id}
    if(edges.isNotEmpty()){Text("Connected rules",fontWeight=FontWeight.Bold);edges.forEach{e->val other=vm.project.nodes.find{it.id==if(e.from==id)e.to else e.from}
        Row(verticalAlignment=Alignment.CenterVertically){Text("${if(e.from==id)"→"else"←"} ${other?.name?:"Missing"}\n${e.relation.label}",Modifier.weight(1f),fontSize=12.sp);IconButton(onClick={vm.edit(vm.project.copy(links=vm.project.links.filterNot{it.id==e.id}))}){Icon(Icons.Default.LinkOff,"Remove connection")}}
    }}
}
@Composable private fun ConditionEditor(vm:WorkbenchVm,n:WbNode,onClose:()->Unit){
    var list by remember{mutableStateOf(n.conditions)};var key by remember{mutableStateOf("network")};var op by remember{mutableStateOf("equals")};var value by remember{mutableStateOf("online")}
    WbDialog("Activation conditions",onClose){
        WbHint("All conditions must match Project settings for this block to be active. These are planning conditions, not live device detection.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            list.forEachIndexed{i,c->Row(verticalAlignment=Alignment.CenterVertically){Text("${c.key} ${c.operator} ${c.value}",Modifier.weight(1f));IconButton(onClick={list=list.filterIndexed{j,_->j!=i}}){Icon(Icons.Default.Delete,"Remove condition")}}}
            ChoiceRow("Environment key",key,WbRules.conditionKeys){key=it;value=when(it){"network"->"online";"architecture"->"arm64";"board"->"Pi 5";"boot source"->"sd";else->"linux"}}
            ChoiceRow("Operator",op,listOf("equals","not equals")){op=it}
            val opts=when(key){"network"->listOf("online","offline");"architecture"->listOf("arm64","arm32","x86_64");"boot source"->listOf("sd","usb","nvme","network");"target OS"->listOf("linux","android","other");else->listOf("Pi 5","Pi 4","Pi 3","Pi Zero 2 W","Other","External x86_64 host")}
            ChoiceRow("Required value",value,opts){value=it}
            OutlinedButton(onClick={if(list.size<16)list=(list+WbCondition(key,op,value)).distinct()},enabled=list.size<16,modifier=Modifier.fillMaxWidth()){Text("Add condition")}
        }
        Button(onClick={vm.project.nodes.find{it.id==n.id}?.let{vm.update(it.copy(conditions=list))};onClose()},modifier=Modifier.fillMaxWidth().padding(bottom=16.dp)){Text("Apply conditions")}
    }
}
@Composable internal fun ConnectEditor(vm:WorkbenchVm,initial:String,target:String?=null,onClose:()->Unit){
    var from by remember{mutableStateOf(initial)};var to by remember{mutableStateOf(target?:vm.project.nodes.firstOrNull{it.id!=initial}?.id.orEmpty())};var relation by remember{mutableStateOf(WbRelation.REQUIRES)}
    val p=vm.project;fun label(id:String)=p.nodes.find{it.id==id}?.let{"${it.name} · ${it.id.take(6)}"}.orEmpty()
    WbDialog("Connect blocks",onClose){
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        WbHint("Connections are explicit rules. Dragging two blocks next to each other does not silently change boot order.")
        Spacer(Modifier.height(16.dp))
        ChoiceRow("A · source",label(from),p.nodes.map{label(it.id)}){v->from=p.nodes.first{label(it.id)==v}.id}
        ChoiceRow("Rule",relation.label,WbRelation.entries.map{it.label}){v->relation=WbRelation.entries.first{it.label==v}}
        ChoiceRow("B · target",label(to),p.nodes.filter{it.id!=from}.map{label(it.id)}){v->to=p.nodes.first{label(it.id)==v}.id}
        Text(relation.explanation,Modifier.padding(vertical=16.dp),color=Color(0xFFE6BBCD))
        Button(onClick={vm.link(from,to,relation);onClose()},enabled=from.isNotBlank()&&to.isNotBlank()&&from!=to,modifier=Modifier.fillMaxWidth()){Text("Create connection")}
        Spacer(Modifier.height(20.dp))
        }
    }
}
@Composable internal fun ProjectSettings(vm:WorkbenchVm,onClose:()->Unit){
    val p=vm.project;var name by remember{mutableStateOf(p.name)};var board by remember{mutableStateOf(p.board)};var arch by remember{mutableStateOf(p.architecture)}
    var disk by remember{mutableStateOf(p.cardMiB.toString())};var ram by remember{mutableStateOf(p.memoryMiB.toString())};var boot by remember{mutableStateOf(p.bootSource)};var net by remember{mutableStateOf(p.network)};var os by remember{mutableStateOf(p.targetOs)};var slot by remember{mutableStateOf(p.activeOsId)};var error by remember{mutableStateOf("")}
    WbDialog("Project & target settings",onClose){
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedTextField(name,{name=it.take(120)},Modifier.fillMaxWidth(),label={Text("Workspace name")},singleLine=true)
            ChoiceRow("Board",board,listOf("Pi 5","Pi 4","Pi 3","Pi Zero 2 W","Other","External x86_64 host")){board=it}
            ChoiceRow("CPU architecture",arch,listOf("arm64","arm32","x86_64")){arch=it}
            ChoiceRow("Target OS family",os,listOf("linux","android","other")){os=it}
            OutlinedTextField(disk,{disk=it},Modifier.fillMaxWidth(),label={Text("Card capacity · MiB")},singleLine=true)
            OutlinedTextField(ram,{ram=it},Modifier.fillMaxWidth(),label={Text("RAM budget · MiB")},singleLine=true)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(16,32,64,128,256).forEach{g->AssistChip(onClick={disk=(g*1024).toString()},label={Text("${g} GiB")})}}
            ChoiceRow("Boot source",boot,listOf("sd","usb","nvme","network")){boot=it};ChoiceRow("Planned network state",net,listOf("online","offline")){net=it}
            val slots=vm.project.nodes.filter{it.type.startsWith("os.")};val labels=listOf("All enabled OS blocks")+slots.map{"${it.name} · ${it.id.take(6)}"}
            ChoiceRow("Active OS slot",slots.find{it.id==slot}?.let{"${it.name} · ${it.id.take(6)}"}?:labels.first(),labels){v->slot=slots.find{"${it.name} · ${it.id.take(6)}"==v}?.id.orEmpty()}
            WbHint("Only one OS normally boots at a time. All enabled OS/storage reservations occupy card capacity, even when a different slot is active. General service blocks remain declarations, not installations.")
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }
        Button(onClick={val d=disk.toLongOrNull();val r=ram.toLongOrNull();if(d==null||d !in 512..8388608L||r==null||r !in 128..1048576L){error="Use card 512–8388608 MiB and RAM 128–1048576 MiB."}else{vm.edit(vm.project.copy(name=name.ifBlank{"Raspberry workspace"},board=board,architecture=arch,cardMiB=d,memoryMiB=r,bootSource=boot,network=net,targetOs=os,activeOsId=slot));onClose()}},modifier=Modifier.fillMaxWidth().padding(vertical=14.dp)){Text("Apply project settings")}
    }
}
@Composable internal fun Tutorial(vm:WorkbenchVm){
    val steps=listOf(
        "Your own workbench" to "This is a separate free canvas. The normal Image Builder and its saved layout remain unchanged. Add OS, boot, storage, drivers or services with +.",
        "Move and inspect" to "Drag a block with one finger. Drag empty space to pan; pinch to zoom. Tap to select, double-tap to inspect. Multi-select lets you move, align, group or duplicate several blocks together.",
        "Connect like a stack" to "A → B with Requires means B needs A before it can start. Before and After create ordering rules; Conflicts disallows simultaneous activation. Wire vertical turns the selected top-to-bottom arrangement into explicit Before rules.",
        "Boot slots are exclusive" to "Two operating systems cannot both be the running base OS. Choose the active OS slot under Project. A selector block is a plan, not an installed MultiBoot firmware. Position on the canvas alone never patches a card.",
        "Conditions and fallbacks" to "Conditions compare against your project environment. Requires on an inactive block is an error. Fallback edges are evaluated only by the failure scenario; they do not install boot recovery logic.",
        "Storage and recovery" to "Enabled storage reservations count against card capacity. Remaining unreserved capacity is the vacuum. Reserved Free Space stays excluded. Normal Pi OS does not need an extra recovery partition; recovery blocks are optional and removable.",
        "Minecraft and exports" to "Minecraft is a tree: Bedrock → Nukkit / Bedrock; Java → Bukkit / Java / Mod Client Forge / Mod Client Fabric. Parent Java and Bedrock rows are selectable too. Exports are configuration plans; provider software is never silently installed.",
        "Save, validate, create" to "Edits autosave per project. Projects, Undo/Redo, JSON import, SVG diagrams and configuration ZIP exports are available. Validate explains errors; EMULATELOAD here is a static scenario report, not real hardware emulation. No image/server downloads happen merely by adding blocks."
    )
    var index by rememberSaveable{mutableIntStateOf(0)}
    AlertDialog(onDismissRequest=vm::seen,icon={Icon(Icons.Default.WorkspacePremium,null,tint=Berry)},title={Text(steps[index].first)},text={Column(Modifier.verticalScroll(rememberScrollState())){Text(steps[index].second);Spacer(Modifier.height(18.dp));LinearProgressIndicator(progress={(index+1).toFloat()/steps.size},modifier=Modifier.fillMaxWidth());Text("${index+1} / ${steps.size}",Modifier.padding(top=8.dp),fontSize=11.sp)}},
        confirmButton={Button(onClick={if(index<steps.lastIndex)index++ else vm.seen()}){Text(if(index==steps.lastIndex)"Start building"else"Next")}},
        dismissButton={Row{if(index>0)TextButton(onClick={index--}){Text("Back")};TextButton(onClick=vm::seen){Text("Skip")}}})
}
