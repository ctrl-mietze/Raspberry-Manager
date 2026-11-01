package dev.mietze.piimager.workbench

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

internal enum class WbTool(val label:String,val group:String) {
    ADD("Add block","Create"),MINECRAFT("Minecraft server","Create"),TEMPLATES("Templates","Create"),NEW("New project","Project"),COPY_PROJECT("Duplicate project","Project"),PROJECTS("Saved projects","Project"),SETTINGS("Project / target settings","Project"),RECOVER("Recover previous snapshot","Project"),
    INSPECT("Inspect selected block","Inspect"),BLOCKS("Blocks / accessible outline","Inspect"),CONNECT("Connect blocks","Rules"),LINK_MODE("Connect by tapping two blocks","Rules"),RULES("All connections","Rules"),WIRE("Wire selected top to bottom","Rules"),DISCONNECT("Disconnect selection","Rules"),
    UNDO("Undo","History"),REDO("Redo","History"),FIT("Fit entire canvas","Canvas"),RESET_VIEW("Reset view","Canvas"),ZOOM_IN("Zoom in","Canvas"),ZOOM_OUT("Zoom out","Canvas"),GRID("Toggle dot grid","Canvas"),SNAP("Toggle snap to grid","Canvas"),MINIMAP("Toggle minimap","Canvas"),
    MULTI("Toggle multiple selection","Selection"),SELECT_ALL("Select all blocks","Selection"),DESELECT("Clear selection","Selection"),DUPLICATE("Duplicate selection with internal links","Selection"),DELETE("Delete selection","Selection"),GROUP("Group selection","Selection"),UNGROUP("Ungroup selection","Selection"),SELECT_GROUP("Select grouped blocks","Selection"),LOCK("Lock positions","Selection"),UNLOCK("Unlock positions","Selection"),ENABLE("Enable selection","Selection"),DISABLE("Disable selection","Selection"),FRONT("Bring selection to front","Selection"),BACK("Send selection to back","Selection"),
    ROW("Arrange in a row","Arrange"),COLUMN("Arrange in a column","Arrange"),GRID_LAYOUT("Arrange as grid","Arrange"),LEFT("Align left edges","Arrange"),TOP("Align top edges","Arrange"),UP("Nudge up","Arrange"),DOWN("Nudge down","Arrange"),NUDGE_LEFT("Nudge left","Arrange"),RIGHT("Nudge right","Arrange"),
    VALIDATE("Validate design","Check"),SIMULATE("EMULATELOAD scenario","Check"),PREVIEW("Preview generated files","Check"),REPORT("Save diagnostic report to Downloads","Check"),
    IMPORT("Import project JSON","Export / import"),JSON("Export editable project","Export / import"),ZIP("Export configuration bundle","Export / import"),SVG("Export SVG diagram","Export / import"),TUTORIAL("Tutorial","Help"),
    FIND("Find a block by name or type","Inspect"),FOCUS("Focus selected block","Inspect"),
    FIT_SELECTION("Fit selected blocks","Canvas"),RELATED("Select connected blocks","Selection"),
    INSPECTOR("Toggle side inspector","Inspect"),RIGHT_ALIGN("Align right edges","Arrange"),
    BOTTOM_ALIGN("Align bottom edges","Arrange"),SPREAD_X("Distribute horizontally","Arrange"),
    SPREAD_Y("Distribute vertically","Arrange"),AUTO_LAYOUT("Smart grid layout","Arrange"),
    COPY_REPORT("Copy validation report","Check")
}
@Composable private fun ToolButton(label:String,icon:ImageVector,enabled:Boolean=true,selected:Boolean=false,click:()->Unit){
    Surface(onClick=click,enabled=enabled,shape=RoundedCornerShape(14.dp),color=if(selected)BerryDark else Color.Transparent){
        Column(Modifier.widthIn(min=54.dp).padding(horizontal=9.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)){
            Icon(icon,label,Modifier.size(20.dp),tint=if(enabled)Color(0xFFF8DBE7)else Color(0xFF6F5A66))
            Text(label,fontSize=10.sp,color=if(enabled)Color(0xFFF8DBE7)else Color(0xFF6F5A66))
        }
    }
}
@Composable private fun WbQuickAction(label:String,icon:ImageVector,modifier:Modifier=Modifier,
    enabled:Boolean=true,active:Boolean=false,click:()->Unit){
    Surface(onClick=click,enabled=enabled,modifier=modifier.height(54.dp),
        shape=RoundedCornerShape(13.dp),
        color=if(active)BerryDark else Color(0xFF341626),
        border=BorderStroke(1.dp,if(active)Berry else Color(0xFF73344F))){
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.Center){
            Icon(icon,label,Modifier.size(20.dp),tint=if(enabled)Color(0xFFFFDCE9)else Color(0xFF836B79))
            Spacer(Modifier.height(2.dp))
            Text(label,fontSize=10.sp,maxLines=1,color=if(enabled)Color(0xFFFADDEA)else Color(0xFF836B79))
        }
    }
}
@Composable private fun WbRailAction(label:String,icon:ImageVector,enabled:Boolean=true,active:Boolean=false,click:()->Unit){
    Surface(onClick=click,enabled=enabled,shape=RoundedCornerShape(12.dp),
        modifier=Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=2.dp).height(52.dp),
        color=if(active)BerryDark else Color.Transparent){
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.Center){
            Icon(icon,label,Modifier.size(20.dp),tint=if(enabled)Color(0xFFF4C9D9)else Color(0xFF77606C))
            Spacer(Modifier.height(3.dp))
            Text(label,fontSize=9.sp,maxLines=1,color=if(enabled)Color(0xFFEAC4D6)else Color(0xFF77606C))
        }
    }
}
@Composable internal fun WorkbenchScreen(vm:WorkbenchVm,repos:List<WbRepoEntry>,onBack:()->Unit,onImport:()->Unit,onExport:(String,Boolean)->Unit,onAttach:(String)->Unit,onProvider:(String)->Unit){
    var modal by rememberSaveable{mutableStateOf("")};var libraryCategory by rememberSaveable{mutableStateOf("All")}
    var mcId by rememberSaveable{mutableStateOf("")};var connectId by rememberSaveable{mutableStateOf("")};var connectTarget by rememberSaveable{mutableStateOf("")};var linkFrom by rememberSaveable{mutableStateOf("")}
    var width by remember{mutableFloatStateOf(360f)};var height by remember{mutableFloatStateOf(540f)}
    var inspectorVisible by rememberSaveable{mutableStateOf(true)};var confirmation by remember{mutableStateOf<WbTool?>(null)}
    val selected=vm.project.nodes.firstOrNull{it.id in vm.selected};val doc=vm.project
    val clipboard=LocalClipboardManager.current
    val analysis=remember(vm.revision,vm.project.id){WbRules.analyze(vm.project)}
    fun openInspect(id:String){vm.selectOnly(id);if(vm.project.nodes.find{it.id==id}?.type=="minecraft"){mcId=id}else{modal="inspector";inspectorVisible=true}}
    fun perform(tool:WbTool){
        when(tool){
            WbTool.ADD->{libraryCategory="All";modal="library"};WbTool.MINECRAFT->mcId="new"
            WbTool.SETTINGS->modal="settings";WbTool.PROJECTS->modal="projects";WbTool.TEMPLATES->modal="templates"
            WbTool.NEW,WbTool.DELETE,WbTool.RECOVER->confirmation=tool
            WbTool.COPY_PROJECT->vm.copyProject()
            WbTool.INSPECT->selected?.let{openInspect(it.id)};WbTool.BLOCKS->modal="blocks"
            WbTool.CONNECT->{connectId=selected?.id?:doc.nodes.firstOrNull()?.id.orEmpty();connectTarget="";if(connectId.isNotBlank())modal="connect"}
            WbTool.LINK_MODE->{linkFrom=selected?.id?:"pending"}
            WbTool.RULES->modal="rules";WbTool.WIRE->vm.wire();WbTool.DISCONNECT->vm.edit(doc.copy(links=doc.links.filterNot{it.from in vm.selected||it.to in vm.selected}))
            WbTool.UNDO->vm.undo();WbTool.REDO->vm.redo();WbTool.FIT->vm.fit(width,height);WbTool.RESET_VIEW->vm.resetView()
            WbTool.ZOOM_IN->vm.zoom(1.2f,width/2,height/2);WbTool.ZOOM_OUT->vm.zoom(1/1.2f,width/2,height/2)
            WbTool.GRID->vm.edit(doc.copy(grid=!doc.grid));WbTool.SNAP->vm.edit(doc.copy(snap=!doc.snap));WbTool.MINIMAP->vm.edit(doc.copy(minimap=!doc.minimap))
            WbTool.MULTI->{vm.multi=!vm.multi};WbTool.SELECT_ALL->vm.selectAll();WbTool.DESELECT->vm.select(null)
            WbTool.DUPLICATE->vm.duplicate();WbTool.GROUP->vm.group();WbTool.UNGROUP->vm.ungroup();WbTool.SELECT_GROUP->vm.selectGroup()
            WbTool.LOCK->vm.lock(true);WbTool.UNLOCK->vm.lock(false);WbTool.ENABLE->vm.enable(true);WbTool.DISABLE->vm.enable(false)
            WbTool.FRONT->vm.edit(doc.copy(nodes=doc.nodes.filter{it.id !in vm.selected}+doc.nodes.filter{it.id in vm.selected}))
            WbTool.BACK->vm.edit(doc.copy(nodes=doc.nodes.filter{it.id in vm.selected}+doc.nodes.filter{it.id !in vm.selected}))
            WbTool.ROW->vm.arrange("row");WbTool.COLUMN->vm.arrange("column");WbTool.GRID_LAYOUT->vm.arrange("grid");WbTool.LEFT->vm.arrange("left");WbTool.TOP->vm.arrange("top")
            WbTool.UP,WbTool.DOWN,WbTool.NUDGE_LEFT,WbTool.RIGHT->{val dx=if(tool==WbTool.NUDGE_LEFT)-16f else if(tool==WbTool.RIGHT)16f else 0f;val dy=if(tool==WbTool.UP)-16f else if(tool==WbTool.DOWN)16f else 0f
                vm.edit(doc.copy(nodes=doc.nodes.map{if(it.id in vm.selected&&!it.locked)it.copy(x=(it.x+dx).coerceIn(-20000f,20000f),y=(it.y+dy).coerceIn(-20000f,20000f))else it}))}
            WbTool.VALIDATE->modal="validate";WbTool.SIMULATE->modal="scenario";WbTool.PREVIEW->modal="preview"
            WbTool.REPORT->vm.report(WbRules.report(doc));WbTool.IMPORT->onImport();WbTool.JSON->onExport("json",false);WbTool.SVG->onExport("svg",false);WbTool.ZIP->modal="export"
            WbTool.TUTORIAL->vm.showTutorial()
            WbTool.FIND->modal="find"
            WbTool.FOCUS->selected?.let{vm.focus(it.id,width,height)}
            WbTool.FIT_SELECTION->vm.fitSelection(width,height)
            WbTool.RELATED->vm.selectLinked()
            WbTool.INSPECTOR->inspectorVisible=!inspectorVisible
            WbTool.RIGHT_ALIGN->vm.arrange("right")
            WbTool.BOTTOM_ALIGN->vm.arrange("bottom")
            WbTool.SPREAD_X->vm.arrange("spread_x")
            WbTool.SPREAD_Y->vm.arrange("spread_y")
            WbTool.AUTO_LAYOUT->{vm.arrange("grid");vm.fit(width,height)}
            WbTool.COPY_REPORT->clipboard.setText(AnnotatedString(WbRules.report(doc)))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().background(WbBackground)){
        val landscape=maxWidth>maxHeight
        val sideRail=landscape&&maxWidth>=620.dp
        val sideInspector=landscape&&maxWidth>=1050.dp&&inspectorVisible
        val short=landscape&&maxHeight<480.dp
        val portraitCompact=!landscape&&maxWidth<440.dp
        Column(Modifier.fillMaxSize()){
            Row(Modifier.fillMaxWidth().height(if(short)50.dp else 61.dp).padding(horizontal=6.dp),
                verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back to Image Builder")}
                Surface(shape=RoundedCornerShape(12.dp),color=BerryDark){
                    Icon(Icons.Default.BusinessCenter,null,Modifier.padding(9.dp).size(20.dp),tint=Color(0xFFFFE0E9))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f).clickable{modal="settings"}){
                    Text(if(portraitCompact)"Pi Magic" else "Pi Magic Workbench",
                        fontWeight=FontWeight.Bold,fontSize=if(short)17.sp else 19.sp,
                        maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(if(portraitCompact)"Workbench · ${doc.name}" else doc.name,
                        maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=10.sp,color=Color(0xFFBFA8B6))
                }
                IconButton(onClick={perform(WbTool.FIND)}){Icon(Icons.Default.Search,"Find blocks")}
                IconButton(onClick={modal="tools"}){Icon(Icons.Default.Tune,"All tools")}
                IconButton(onClick={perform(WbTool.ZIP)}){Icon(Icons.Default.IosShare,"Export")}
            }
            if(!sideRail){
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=2.dp),
                    horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    WbQuickAction("Add",Icons.Default.Add,Modifier.weight(1f)){perform(WbTool.ADD)}
                    WbQuickAction("Inspect",Icons.Default.Edit,Modifier.weight(1f),enabled=selected!=null){perform(WbTool.INSPECT)}
                    WbQuickAction("Link",Icons.Default.AccountTree,Modifier.weight(1f),enabled=doc.nodes.size>1,active=linkFrom.isNotBlank()){perform(WbTool.LINK_MODE)}
                    WbQuickAction("Undo",Icons.Default.Undo,Modifier.weight(1f),enabled=vm.canUndo){perform(WbTool.UNDO)}
                    WbQuickAction("Redo",Icons.Default.Redo,Modifier.weight(1f),enabled=vm.canRedo){perform(WbTool.REDO)}
                    WbQuickAction("Fit",Icons.Default.FitScreen,Modifier.weight(1f)){perform(WbTool.FIT)}
                }
            }
            Surface(Modifier.fillMaxWidth().padding(horizontal=if(landscape)8.dp else 12.dp,vertical=4.dp),
                shape=RoundedCornerShape(12.dp),color=Color(0xFF1B131C),border=BorderStroke(1.dp,Color(0xFF49303D))){
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=4.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("${doc.nodes.size} blocks · ${doc.links.size} links",fontSize=11.sp,color=Color(0xFFE6C9D7),maxLines=1)
                    Text("·",color=Color(0xFF6F5664))
                    Text("${(doc.cardMiB-analysis.reservedMiB).coerceAtLeast(0)/1024f} GiB free",
                        fontSize=11.sp,color=Color(0xFFD7A4BD),maxLines=1)
                    if(analysis.errors>0)Text("${analysis.errors} issue(s)",fontSize=11.sp,color=Color(0xFFFF908B))
                    TextButton(onClick={perform(WbTool.ZOOM_OUT)},contentPadding=PaddingValues(0.dp),
                        modifier=Modifier.size(28.dp)){Icon(Icons.Default.Remove,"Zoom out",Modifier.size(17.dp))}
                    Text("${(vm.view.zoom*100).toInt()}%",fontSize=11.sp,color=Color(0xFFE6C9D7))
                    TextButton(onClick={perform(WbTool.ZOOM_IN)},contentPadding=PaddingValues(0.dp),
                        modifier=Modifier.size(28.dp)){Icon(Icons.Default.Add,"Zoom in",Modifier.size(17.dp))}
                    TextButton(onClick={perform(WbTool.SNAP)},contentPadding=PaddingValues(horizontal=5.dp),
                        modifier=Modifier.height(28.dp)){Text(if(doc.snap)"Snap on"else"Snap off",fontSize=10.sp)}
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth()){
                if(sideRail){
                    Surface(Modifier.width(76.dp).fillMaxHeight(),color=Color(0xFF191019)){
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=3.dp)){
                            WbRailAction("Add",Icons.Default.Add){perform(WbTool.ADD)}
                            WbRailAction("OS",Icons.Default.Computer){libraryCategory="Operating systems";modal="library"}
                            WbRailAction("Boot",Icons.Default.PowerSettingsNew){libraryCategory="Boot";modal="library"}
                            WbRailAction("Storage",Icons.Default.Storage){libraryCategory="Storage";modal="library"}
                            WbRailAction("Minecraft",Icons.Default.SportsEsports){mcId="new"}
                            WbRailAction("Find",Icons.Default.Search){perform(WbTool.FIND)}
                            WbRailAction("Inspect",Icons.Default.Edit,enabled=selected!=null){perform(WbTool.INSPECT)}
                            WbRailAction("Connect",Icons.Default.Link,enabled=doc.nodes.size>1,active=linkFrom.isNotBlank()){perform(WbTool.LINK_MODE)}
                            WbRailAction("Undo",Icons.Default.Undo,enabled=vm.canUndo){perform(WbTool.UNDO)}
                            WbRailAction("Redo",Icons.Default.Redo,enabled=vm.canRedo){perform(WbTool.REDO)}
                            WbRailAction("Fit",Icons.Default.FitScreen){perform(WbTool.FIT)}
                            WbRailAction("Layout",Icons.Default.GridView){perform(WbTool.AUTO_LAYOUT)}
                            WbRailAction("Tools",Icons.Default.Tune){modal="tools"}
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()){
                    WorkbenchCanvas(vm,Modifier.fillMaxSize(),onSize={w,h->width=w;height=h},onTap={id->
                        if(id!=null&&linkFrom.isNotBlank()){
                            if(linkFrom=="pending")linkFrom=id
                            else if(linkFrom!=id){connectId=linkFrom;connectTarget=id;linkFrom="";modal="connect"}
                        }else{vm.select(id);if(id!=null&&doc.nodes.find{it.id==id}?.type=="minecraft")mcId=id}
                    },onOpen={id->
                        if(sideInspector){vm.selectOnly(id);if(doc.nodes.find{it.id==id}?.type=="minecraft")mcId=id}
                        else openInspect(id)
                    })
                    if(linkFrom.isNotBlank()){
                        Surface(Modifier.align(Alignment.TopCenter).padding(9.dp),
                            shape=RoundedCornerShape(12.dp),color=Color(0xFF3C1D30),
                            border=BorderStroke(1.dp,Berry.copy(alpha=.55f))){
                            Row(Modifier.padding(start=12.dp,end=5.dp),verticalAlignment=Alignment.CenterVertically){
                                Icon(Icons.Default.AccountTree,null,tint=Berry,modifier=Modifier.size(16.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(if(linkFrom=="pending")"Tap source block"else"Tap destination block",
                                    fontSize=11.sp,color=Color(0xFFEED3E0))
                                TextButton(onClick={linkFrom=""}){Text("Cancel",fontSize=11.sp)}
                            }
                        }
                    }
                    if(doc.nodes.isEmpty()&&!vm.busy){
                        Surface(Modifier.align(Alignment.Center).padding(18.dp),shape=RoundedCornerShape(22.dp),
                            color=Color(0xFF1C141C),border=BorderStroke(1.dp,Color(0xFF593045))){
                            Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,
                                verticalArrangement=Arrangement.spacedBy(10.dp)){
                                Icon(Icons.Default.AccountTree,null,tint=Berry,modifier=Modifier.size(30.dp))
                                Text("Build your Pi workspace",fontWeight=FontWeight.Bold,fontSize=17.sp)
                                Text("Add components or start with a template.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    Button(onClick={perform(WbTool.ADD)}){Text("Add block")}
                                    OutlinedButton(onClick={modal="templates"}){Text("Templates")}
                                }
                            }
                        }
                    }
                }
                if(sideInspector){
                    Surface(Modifier.width(304.dp).fillMaxHeight(),color=Color(0xFF181119),
                        border=BorderStroke(1.dp,Color(0xFF3A2633))){
                        if(selected!=null)NodeInspector(vm,selected,Modifier.fillMaxSize(),onAttach,
                            {mcId=it},{connectId=it;connectTarget="";modal="connect"},onProvider)
                        else Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                            Icon(Icons.Default.TouchApp,null,tint=Berry,modifier=Modifier.size(32.dp))
                            Text("Inspector",fontWeight=FontWeight.Bold)
                            Text("Select a block to edit its settings, dependencies and files.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick={modal="blocks"}){Text("Block outline")}
                        }
                    }
                }
            }
            Surface(Modifier.fillMaxWidth(),color=Color(0xFF181018),
                border=BorderStroke(1.dp,Color(0xFF392432))){
                Row(Modifier.fillMaxWidth().height(if(short)44.dp else 56.dp).padding(horizontal=9.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f).padding(start=5.dp)){
                        Text(selected?.name?:"Raspberry workspace",fontSize=12.sp,
                            maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                        Text(vm.status,fontSize=10.sp,color=Color(0xFFAE8E9F),maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    if(selected!=null&&!sideInspector)IconButton(onClick={openInspect(selected.id)}){
                        Icon(Icons.Default.Edit,"Edit selection",Modifier.size(21.dp),tint=Color(0xFFE6BDD0))
                    }
                    IconButton(onClick={perform(WbTool.BLOCKS)}){
                        Icon(Icons.Default.ViewList,"Block outline",Modifier.size(21.dp),tint=Color(0xFFE6BDD0))
                    }
                    IconButton(onClick={perform(WbTool.AUTO_LAYOUT)},enabled=doc.nodes.size>1){
                        Icon(Icons.Default.GridView,"Auto layout",Modifier.size(21.dp),tint=Color(0xFFE6BDD0))
                    }
                    IconButton(onClick={perform(WbTool.VALIDATE)}){
                        BadgedBox(badge={if(analysis.errors>0)Badge{Text(analysis.errors.toString())}}){
                            Icon(Icons.Default.FactCheck,"Validate workspace",Modifier.size(21.dp),tint=Berry)
                        }
                    }
                }
            }
        }
        if(vm.busy)Surface(Modifier.fillMaxSize().clickable{},color=Color.Black.copy(alpha=.7f)){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator(color=Berry);Spacer(Modifier.height(18.dp));Text(vm.status)}}
    }
    if(vm.error!=null)AlertDialog(onDismissRequest=vm::dismissError,title={Text("Workbench")},text={Text(vm.error.orEmpty())},confirmButton={TextButton(onClick=vm::dismissError){Text("OK")}})
    if(vm.tutorial&&!vm.busy)Tutorial(vm)
    if(mcId.isNotBlank())MinecraftChoice(onChoose={type->if(mcId=="new")vm.add(type,width,height)else vm.chooseMinecraft(mcId,type);mcId="";modal="inspector"},onClose={mcId=""})
    when(modal){
        "find"->WbDialog("Find in workbench",{modal=""}){
            var search by rememberSaveable{mutableStateOf("")}
            OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("Search blocks and groups")},
                singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)})
            val matches=doc.nodes.filter{(it.name+" "+it.type+" "+it.group+" "+WbCatalog.kind(it.type).category).contains(search,true)}
            Text("${matches.size} of ${doc.nodes.size} blocks",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)){
                items(matches,key={it.id}){node->
                    Card(onClick={vm.focus(node.id,width,height);modal=""},modifier=Modifier.fillMaxWidth(),
                        colors=CardDefaults.cardColors(containerColor=Color(0xFF24171F)),shape=RoundedCornerShape(14.dp)){
                        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Default.CenterFocusStrong,null,tint=Berry);Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)){Text(node.name,fontWeight=FontWeight.Medium);Text("${node.type} · ${node.group.ifEmpty{"Ungrouped"}}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            Icon(Icons.Default.ChevronRight,null,tint=Berry)
                        }
                    }
                }
            }
        }
        "library"->BlockLibrary(libraryCategory,repos,{vm.add(it,width,height);modal=""},{r->vm.addRepo(r,width,height);modal=""},{modal=""})
        "inspector"->selected?.let{n->WbDialog("Block settings",{modal=""}){NodeInspector(vm,n,Modifier.weight(1f).fillMaxWidth(),onAttach,{mcId=it},{connectId=it;connectTarget="";modal="connect"},onProvider)}}
        "settings"->ProjectSettings(vm){modal=""}
        "connect"->ConnectEditor(vm,connectId,connectTarget.takeIf{it.isNotBlank()}){modal=""}
        "tools"->ToolMenu{t->modal="";if(t!=null)perform(t)}
        "blocks"->WbDialog("Blocks & groups",{modal=""}){
            WbSwitch("Multiple selection",vm.multi,{vm.multi=it})
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(doc.nodes,key={it.id}){n->Card(onClick={vm.select(n.id)},shape=RoundedCornerShape(15.dp),colors=CardDefaults.cardColors(containerColor=if(n.id in vm.selected)Color(0xFF492336)else Color(0xFF21151E))){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(n.name,fontWeight=FontWeight.SemiBold);Text("${WbCatalog.kind(n.type).category} · ${n.group.ifBlank{"No group"}} · ${if(n.enabled)"on"else"off"}",fontSize=11.sp)};IconButton(onClick={vm.focus(n.id,width,height);modal=""}){Icon(Icons.Default.CenterFocusStrong,"Focus")};IconButton(onClick={vm.selectOnly(n.id);modal="inspector"}){Icon(Icons.Default.Edit,"Inspect")}}}}}
        }
        "rules"->WbDialog("Connections & rules",{modal=""}){
            WbHint("Arrow labels define semantics; coordinates alone do not. Fallback is a scenario rule, not a real boot recovery loader.")
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=12.dp)){items(doc.links,key={it.id}){e->Card{Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("${doc.nodes.find{it.id==e.from}?.name?:"Missing"} → ${doc.nodes.find{it.id==e.to}?.name?:"Missing"}");Text(e.relation.label,color=Berry);Text(e.relation.explanation,fontSize=11.sp)};IconButton(onClick={vm.edit(vm.project.copy(links=vm.project.links.filterNot{it.id==e.id}))}){Icon(Icons.Default.LinkOff,"Remove rule")}}}}}
            Button(onClick={perform(WbTool.CONNECT)},enabled=doc.nodes.size>1,modifier=Modifier.fillMaxWidth().padding(bottom=12.dp)){Text("Add a connection")}
        }
        "projects"->ProjectList(vm,{modal=""},{perform(it)})
        "templates"->WbDialog("Workspace templates",{modal=""}){WbHint("Each template creates a new project. Your existing workspace remains saved separately.");LazyColumn(Modifier.weight(1f)){items(WbTemplates.names){name->TextButton(onClick={vm.useTemplate(name);modal="";vm.fit(width,height)},modifier=Modifier.fillMaxWidth()){Text(name,Modifier.fillMaxWidth().padding(12.dp))}}}}
        "validate","scenario","preview"->Diagnostics(vm,modal,onClose={modal=""})
        "export"->{var include by remember{mutableStateOf(false)};AlertDialog(onDismissRequest={modal=""},title={Text("Export configuration bundle")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Project JSON, SVG diagram, validation report and Minecraft configs/launch helpers. No OS images, installed services or boot-success claims.");WbSwitch("Include attached files",include,{include=it},"Local content-addressed files only, at most 256 MiB total.");if(analysis.errors>0)Text("${analysis.errors} errors: start helpers will be withheld until corrected.",color=MaterialTheme.colorScheme.error)}},confirmButton={Button(onClick={modal="";onExport("zip",include)}){Text("Choose destination")}},dismissButton={TextButton(onClick={modal=""}){Text("Cancel")}})}
    }
    confirmation?.let{tool->AlertDialog(onDismissRequest={confirmation=null},title={Text(tool.label+"?")},text={Text(when(tool){WbTool.DELETE->"Remove ${vm.selected.size} selected blocks and their connections? Undo remains available.";WbTool.RECOVER->"Open the previous saved snapshot as a separate project?";else->"Create a new workspace? The existing one remains saved under Projects."})},confirmButton={Button(onClick={when(tool){WbTool.DELETE->vm.remove();WbTool.RECOVER->vm.recover();else->vm.newProject()};confirmation=null}){Text("Continue")}},dismissButton={TextButton(onClick={confirmation=null}){Text("Cancel")}})}
}
@Composable private fun ToolMenu(onAction:(WbTool?)->Unit){
    var query by rememberSaveable{mutableStateOf("")};var category by rememberSaveable{mutableStateOf("All")}
    WbDialog("Workbench tools",{onAction(null)}){
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Find a tool")},leadingIcon={Icon(Icons.Default.Search,null)})
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){(listOf("All")+WbTool.entries.map{it.group}.distinct()).forEach{g->FilterChip(category==g,{category=g},label={Text(g)})}}
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)){items(WbTool.entries.filter{(category=="All"||it.group==category)&&(it.label+it.group).contains(query,true)}){t->ListItem(headlineContent={Text(t.label)},supportingContent={Text(t.group)},leadingContent={Icon(Icons.Default.ChevronRight,null,tint=Berry)},modifier=Modifier.clickable{onAction(t)})}}
    }
}
@Composable private fun ProjectList(vm:WorkbenchVm,onClose:()->Unit,action:(WbTool)->Unit){
    var projects by remember{mutableStateOf(emptyList<Pair<WbProject,Long>>())};LaunchedEffect(Unit){vm.listProjects{projects=it}}
    WbDialog("Your workspaces",onClose){
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={onClose();action(WbTool.NEW)}){Text("New")};OutlinedButton(onClick={onClose();action(WbTool.IMPORT)}){Text("Import JSON")}}
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=14.dp)){items(projects,key={it.first.id}){(p,time)->Card(onClick={vm.open(p);onClose()},shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text(p.name,fontWeight=FontWeight.Bold);Text("${p.nodes.size} blocks · ${p.board}",fontSize=12.sp);Text(DateFormat.getDateTimeInstance().format(Date(time)),fontSize=10.sp)}}}}
    }
}
@Composable private fun Diagnostics(vm:WorkbenchVm,initial:String,onClose:()->Unit){
    var tab by rememberSaveable{mutableStateOf(initial)};var failed by remember{mutableStateOf(emptySet<String>())}
    val p=vm.project;val analysis=remember(p){WbRules.analyze(p)};val clipboard=LocalClipboardManager.current
    var file by rememberSaveable{mutableStateOf("README.txt")}
    val outputs=remember(p){runCatching{WbExport.files(p)}.getOrElse{mapOf("error.txt" to (it.message?:"Invalid project"))}}
    val text=when(tab){"scenario"->WbRules.simulate(p,failed).joinToString("\n");"preview"->outputs[file]?:outputs.values.firstOrNull().orEmpty();else->WbRules.report(p)}
    WbDialog("Validate & preview",onClose){
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("validate" to "Analysis","scenario" to "EMULATELOAD","preview" to "Generated files").forEach{(key,label)->FilterChip(tab==key,{tab=key},label={Text(label)})}}
        if(tab=="scenario"){
            Text("Simulate failure of selected blocks. No real processes are started.",fontSize=12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){p.nodes.filter{it.id in analysis.active}.forEach{n->FilterChip(n.id in failed,{failed=if(n.id in failed)failed-n.id else failed+n.id},label={Text(n.name)})}}
        }
        if(tab=="preview")ChoiceRow("File",file,outputs.keys.toList()){file=it}
        if(tab=="validate")Text("${analysis.errors} errors · ${analysis.warnings} warnings",fontWeight=FontWeight.Bold,color=if(analysis.errors>0)MaterialTheme.colorScheme.error else Color(0xFFB6CB91))
        Surface(Modifier.weight(1f).fillMaxWidth().padding(vertical=10.dp),shape=RoundedCornerShape(16.dp),color=Color(0xFF1C141D)){
            androidx.compose.foundation.text.selection.SelectionContainer{Text(text,Modifier.verticalScroll(rememberScrollState()).padding(16.dp),fontSize=12.sp)}
        }
        Row(Modifier.fillMaxWidth().padding(bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Button(onClick={vm.report(text)},modifier=Modifier.weight(1f)){Text("Save to Downloads")}
            OutlinedButton(onClick={clipboard.setText(AnnotatedString(text))}){Text("Copy")}
        }
    }
}
