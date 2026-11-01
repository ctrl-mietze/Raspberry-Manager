package dev.mietze.piimager.workbench

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class WbHistory(private val limit:Int=60) {
    private val undo=ArrayDeque<WbProject>();private val redo=ArrayDeque<WbProject>()
    val canUndo get()=undo.isNotEmpty();val canRedo get()=redo.isNotEmpty()
    fun checkpoint(p:WbProject){undo.addLast(p);while(undo.size>limit)undo.removeFirst();redo.clear()}
    fun undo(current:WbProject):WbProject?{if(undo.isEmpty())return null;redo.addLast(current);return undo.removeLast()}
    fun redo(current:WbProject):WbProject?{if(redo.isEmpty())return null;undo.addLast(current);return redo.removeLast()}
    fun clear(){undo.clear();redo.clear()}
}
internal class WbRepository(private val app:Application) {
    private val root=File(app.filesDir,"pi_magic_workbench").apply{mkdirs()}
    private val blobs=File(root,"files").apply{mkdirs()}
    private val prefs=app.getSharedPreferences("pi_magic_workbench_settings",0)
    private fun file(id:String):File { require(Regex("[A-Za-z0-9_-]{1,80}").matches(id));return File(root,"$id.rwb.json") }
    @Synchronized fun save(p:WbProject){
        val bytes=WbCodec.encode(p).toByteArray();require(bytes.size<=WbCodec.MAX_BYTES)
        val target=file(p.id);if(target.isFile)target.copyTo(File(root,"${p.id}.previous.json"),overwrite=true)
        val atomic=AtomicFile(target);val out=atomic.startWrite()
        try{out.write(bytes);atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e}
        prefs.edit().putString("last",p.id).apply()
    }
    fun load(id:String):WbProject = AtomicFile(file(id)).openRead().use{WbCodec.decode(readBounded(it,WbCodec.MAX_BYTES).toString(Charsets.UTF_8))}
    fun lastId():String?=prefs.getString("last",null)
    fun last():WbProject?=lastId()?.let{load(it)}
    fun list():List<Pair<WbProject,Long>> = root.listFiles().orEmpty().filter{it.name.endsWith(".rwb.json")}.sortedByDescending{it.lastModified()}.take(100).mapNotNull{f->runCatching{load(f.name.removeSuffix(".rwb.json")) to f.lastModified()}.getOrNull()}
    fun previous(id:String):WbProject {val f=File(root,"$id.previous.json");require(f.isFile){"No previous snapshot"};return f.inputStream().use{WbCodec.decode(readBounded(it,WbCodec.MAX_BYTES).toString(Charsets.UTF_8))}}
    fun tutorialSeen()=prefs.getBoolean("tutorial",false)
    fun seen(){prefs.edit().putBoolean("tutorial",true).apply()}
    fun readUri(uri:Uri):WbProject { require(uri.scheme=="content"){"Use the Android document picker"};return app.contentResolver.openInputStream(uri)?.use{WbCodec.decode(readBounded(it,WbCodec.MAX_BYTES).toString(Charsets.UTF_8))}?:error("Cannot open project") }
    fun importFile(uri:Uri):WbAttachment {
        require(uri.scheme=="content"){"Use the Android document picker"}
        val name=app.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0) else null}.orEmpty().take(160).ifBlank{"attachment"}
        val tmp=File.createTempFile("import-",".part",blobs);var size=0L;val hash=MessageDigest.getInstance("SHA-256");val head=ByteArrayOutputStream()
        try{
            val input=app.contentResolver.openInputStream(uri)?:error("Cannot open selected file")
            input.use{i->tmp.outputStream().use{o->val buf=ByteArray(32768);while(true){val n=i.read(buf);if(n<0)break;size+=n;require(size<=64L*1024*1024){"Attachments are limited to 64 MiB; keep larger OS images as source references."};hash.update(buf,0,n);o.write(buf,0,n);if(head.size()<16)head.write(buf,0,minOf(n,16-head.size()))}}}
            val digest=hash.digest().joinToString(""){"%02x".format(it.toInt()and 255)}
            val b=head.toByteArray();val kind=when{b.size>=4&&b[0]==0x7f.toByte()&&b[1]==69.toByte()&&b[2]==76.toByte()&&b[3]==70.toByte()->"ELF (not executed)"
                b.size>=4&&b[0]==80.toByte()&&b[1]==75.toByte()->if(name.endsWith(".jar",true))"JAR/ZIP" else "ZIP"
                name.endsWith(".dtbo",true)||name.endsWith(".dtb",true)->"Device-tree reference";else->"File reference"}
            val dst=File(blobs,digest);if(!dst.exists()){check(tmp.renameTo(dst)){"Cannot store attachment"}}
            return WbAttachment(name,digest,size,kind)
        }finally{tmp.delete()}
    }
    fun bundle(p:WbProject,out:OutputStream,includeFiles:Boolean=false){
        ZipOutputStream(BufferedOutputStream(out)).use{zip->
            val files=WbExport.files(p);files.forEach{(name,body)->zip.putNextEntry(ZipEntry(name));zip.write(body.toByteArray());zip.closeEntry()}
            if(includeFiles){var total=0L;p.nodes.mapNotNull{it.attachment}.distinctBy{it.sha256}.forEach{a->
                require(Regex("[0-9a-f]{64}").matches(a.sha256));val f=File(blobs,a.sha256)
                require(f.isFile){"Missing local attachment ${a.name}. Reattach it or export without files."}
                total+=f.length();require(total<=256L*1024*1024){"Attachment bundle exceeds 256 MiB"}
                val hash=MessageDigest.getInstance("SHA-256");f.inputStream().use{input->val b=ByteArray(32768);while(true){val n=input.read(b);if(n<0)break;hash.update(b,0,n)}}
                require(hash.digest().joinToString(""){"%02x".format(it.toInt()and 255)}==a.sha256){"Attachment changed"}
                zip.putNextEntry(ZipEntry("attachments/${a.sha256}.bin"));f.inputStream().use{it.copyTo(zip)};zip.closeEntry()
            }}
        }
    }
    fun exportUri(uri:Uri,p:WbProject,format:String,includeFiles:Boolean=false){
        require(uri.scheme=="content")
        val out=app.contentResolver.openOutputStream(uri,"wt")?:error("Cannot write document")
        out.use{when(format){"zip"->bundle(p,it,includeFiles);"svg"->it.write(WbExport.svg(p).toByteArray());"txt"->it.write(WbRules.report(p).toByteArray());else->it.write(WbCodec.encode(p).toByteArray())}}
    }
    fun reportDownload(name:String,text:String):String {
        if(Build.VERSION.SDK_INT>=29){
            val v=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/RaspberryManager-Workbench");put(MediaStore.MediaColumns.IS_PENDING,1)}
            val uri=app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v)?:error("Cannot create Downloads report")
            try{app.contentResolver.openOutputStream(uri)?.use{it.write(text.toByteArray())}?:error("Cannot write report");app.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null);return "Download/RaspberryManager-Workbench/$name"}
            catch(e:Exception){app.contentResolver.delete(uri,null,null);throw e}
        }
        val f=File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),name);f.writeText(text);return f.absolutePath
    }
    private fun readBounded(input:InputStream,max:Int):ByteArray {val out=ByteArrayOutputStream();val buf=ByteArray(8192);while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=max){"File too large"};out.write(buf,0,n)};return out.toByteArray()}
}

internal class WorkbenchVm(app:Application):AndroidViewModel(app) {
    private val repo=WbRepository(app);private val mutex=Mutex();private val history=WbHistory()
    var project by mutableStateOf(WbProject());private set
    var selected by mutableStateOf(emptySet<String>());private set
    var view by mutableStateOf(WbView());private set
    var busy by mutableStateOf(true);private set
    var status by mutableStateOf("Loading workspace…");private set
    var error by mutableStateOf<String?>(null);private set
    var revision by mutableIntStateOf(0);private set
    var tutorial by mutableStateOf(!repo.tutorialSeen());private set
    var multi by mutableStateOf(false)
    private var recoveryId:String?=null
    private var dragOriginal:WbProject?=null
    val canUndo get()=history.canUndo
    val canRedo get()=history.canRedo
    init{viewModelScope.launch{try{val saved=withContext(Dispatchers.IO){repo.last()};if(saved!=null)project=saved;status="Ready"}catch(e:Exception){recoveryId=repo.lastId();error="Last project could not be read: ${e.message}. It was not overwritten. Use Projects to recover a previous snapshot.";status="Recovery available"}finally{busy=false}}}
    fun dismissError(){error=null}
    private fun save(snapshot:WbProject){status="Saving…";viewModelScope.launch{try{withContext(Dispatchers.IO){mutex.withLock{repo.save(snapshot)}};if(project==snapshot)status="Saved locally"}catch(e:Exception){error="Save failed: ${e.message}";status="Unsaved changes"}}}
    fun edit(next:WbProject){if(next==project)return;history.checkpoint(project);project=next;selected=selected.intersect(next.nodes.map{it.id}.toSet());revision++;save(next)}
    fun update(n:WbNode)=edit(project.copy(nodes=project.nodes.map{if(it.id==n.id)n else it}))
    fun select(id:String?){selected=if(id==null)emptySet() else if(multi){if(id in selected)selected-id else selected+id}else setOf(id)}
    fun selectOnly(id:String){selected=if(project.nodes.any{it.id==id})setOf(id)else emptySet()}
    fun selectAll(){selected=project.nodes.map{it.id}.toSet()}
    fun selectLinked(){
        if(selected.isEmpty())return
        val related=project.links.filter{it.from in selected||it.to in selected}.flatMap{listOf(it.from,it.to)}
        selected=selected+related.filter{id->project.nodes.any{it.id==id}}
    }
    fun selectGroup(){val groups=project.nodes.filter{it.id in selected}.map{it.group}.filter{it.isNotBlank()};selected+=project.nodes.filter{it.group in groups}.map{it.id}}
    fun pan(dx:Float,dy:Float){view=view.copy(panX=(view.panX+dx).coerceIn(-50000f,50000f),panY=(view.panY+dy).coerceIn(-50000f,50000f))}
    fun zoom(f:Float,cx:Float,cy:Float){view=WbGeometry.zoom(view,f,cx,cy)}
    fun fit(w:Float,h:Float){view=WbGeometry.fit(project.nodes,w,h)}
    fun fitSelection(w:Float,h:Float){
        val chosen=project.nodes.filter{it.id in selected}
        view=WbGeometry.fit(chosen.ifEmpty{project.nodes},w,h)
    }
    fun focus(id:String,w:Float,h:Float){selectOnly(id);project.nodes.find{it.id==id}?.let{view=WbGeometry.fit(listOf(it),w,h)}}
    fun resetView(){view=WbView()}
    fun beginDrag(id:String){if(id !in selected)selected=setOf(id);dragOriginal=project}
    fun drag(dx:Float,dy:Float){project=project.copy(nodes=project.nodes.map{if(it.id in selected&&!it.locked)it.copy(x=(it.x+dx).coerceIn(-20000f,20000f),y=(it.y+dy).coerceIn(-20000f,20000f))else it})}
    fun endDrag(){val original=dragOriginal?:return;dragOriginal=null;val next=project.copy(nodes=project.nodes.map{if(it.id in selected&&!it.locked)it.copy(x=WbGeometry.snap(it.x,project.snap),y=WbGeometry.snap(it.y,project.snap))else it});if(next!=original){history.checkpoint(original);project=next;revision++;save(next)}}
    fun undo(){history.undo(project)?.let{project=it;revision++;selected=emptySet();save(it)}}
    fun redo(){history.redo(project)?.let{project=it;revision++;selected=emptySet();save(it)}}
    fun add(type:String,w:Float,h:Float){try{require(project.nodes.size<WB_MAX_NODES){"256 block limit reached"};val pos=WbGeometry.world(w/2-WB_NODE_W*view.zoom/2,h/2-WB_NODE_H*view.zoom/2,view);val n=WbCatalog.create(type,WbGeometry.snap(pos.first,project.snap),WbGeometry.snap(pos.second,project.snap));edit(project.copy(nodes=project.nodes+n));selected=setOf(n.id)}catch(e:Exception){error=e.message}}
    fun addRepo(entry:WbRepoEntry,w:Float,h:Float){
        if(project.nodes.size>=WB_MAX_NODES){error="256 block limit reached";return}
        add("os.custom",w,h)
        val id=selected.singleOrNull()?:return
        project.nodes.find{it.id==id}?.let{update(it.copy(name=entry.name.take(100),params=it.params+("sourceUrl" to entry.website),notes=entry.description.take(4096)))}
    }
    fun duplicate(){runCatching{WbEdits.duplicate(project,selected)}.onSuccess{edit(it)}.onFailure{error=it.message}}
    fun remove(){edit(WbEdits.remove(project,selected));selected=emptySet()}
    fun arrange(mode:String){edit(WbEdits.arrange(project,selected.ifEmpty{project.nodes.map{it.id}.toSet()},mode))}
    fun wire(){runCatching{WbEdits.wireVertical(project,selected.ifEmpty{project.nodes.map{it.id}.toSet()})}.onSuccess{edit(it)}.onFailure{error=it.message}}
    fun group(){val name="Group ${project.nodes.map{it.group}.distinct().count{it.isNotBlank()}+1}";edit(project.copy(nodes=project.nodes.map{if(it.id in selected)it.copy(group=name)else it}))}
    fun ungroup(){edit(project.copy(nodes=project.nodes.map{if(it.id in selected)it.copy(group="")else it}))}
    fun lock(value:Boolean){edit(project.copy(nodes=project.nodes.map{if(it.id in selected)it.copy(locked=value)else it}))}
    fun enable(value:Boolean){edit(project.copy(nodes=project.nodes.map{if(it.id in selected)it.copy(enabled=value)else it}))}
    fun link(from:String,to:String,relation:WbRelation){if(from==to){error="Choose two different blocks";return};if(project.links.any{it.from==from&&it.to==to&&it.relation==relation})return;if(project.links.size>=WB_MAX_LINKS){error="Connection limit reached";return};edit(project.copy(links=project.links+WbLink(from=from,to=to,relation=relation)))}
    fun addRuntimeFor(n:WbNode){if(project.nodes.size>=WB_MAX_NODES)return;val java=project.nodes.find{it.type=="runtime.java"&&it.number("major")>=n.number("javaMajor")}?:WbCatalog.create("runtime.java",n.x,n.y-176).copy(params=mapOf("major" to n.value("javaMajor"),"distribution" to "OpenJDK"))
        val next=if(project.nodes.any{it.id==java.id})project else project.copy(nodes=project.nodes+java);val e=WbLink(from=java.id,to=n.id)
        edit(next.copy(links=if(next.links.any{it.from==java.id&&it.to==n.id})next.links else next.links+e))}
    fun chooseMinecraft(id:String,type:String){val old=project.nodes.find{it.id==id}?:return;if(old.type==type)return;val fresh=WbCatalog.create(type,old.x,old.y);update(fresh.copy(id=old.id,group=old.group,enabled=old.enabled,locked=old.locked,optional=old.optional,conditions=old.conditions,notes=old.notes))}
    fun seen(){tutorial=false;repo.seen()}
    fun showTutorial(){tutorial=true}
    fun newProject(){recoveryId=null;edit(WbProject());history.clear();revision++;view=WbView()}
    fun useTemplate(type:String){recoveryId=null;edit(WbTemplates.make(type));history.clear();revision++;view=WbView()}
    fun copyProject(){recoveryId=null;edit(WbCodec.asCopy(project));history.clear();revision++}
    fun listProjects(done:(List<Pair<WbProject,Long>>)->Unit){viewModelScope.launch{val result=withContext(Dispatchers.IO){repo.list()};done(result)}}
    fun open(p:WbProject){recoveryId=null;project=p;selected=emptySet();view=WbView();history.clear();revision++;save(p)}
    fun recover(){val id=recoveryId?:project.id;task("Restoring snapshot…"){val recovered=repo.previous(id);withContext(Dispatchers.Main){open(WbCodec.asCopy(recovered))}}}
    fun importProject(uri:Uri){task("Importing…"){val p=repo.readUri(uri);withContext(Dispatchers.Main){open(WbCodec.asCopy(p))}}}
    fun attach(id:String,uri:Uri){task("Reading file / SHA-256…"){val a=repo.importFile(uri);withContext(Dispatchers.Main){project.nodes.find{it.id==id}?.let{update(it.copy(attachment=a))}}}}
    fun export(uri:Uri,format:String,include:Boolean=false){val snapshot=project;task("Exporting $format…"){repo.exportUri(uri,snapshot,format,include)}}
    fun report(text:String){task("Saving report…"){val path=repo.reportDownload("Workbench-report-${System.currentTimeMillis()}.txt",text);withContext(Dispatchers.Main){status=path}}}
    private fun task(label:String,block:suspend()->Unit){if(busy)return;busy=true;status=label;viewModelScope.launch{try{withContext(Dispatchers.IO){block()};if(status==label)status="Completed"}catch(e:Exception){error=e.message?:"Operation failed";status="Operation failed"}finally{busy=false}}}
}
internal object WbTemplates {
    val names=listOf("Empty canvas","Pi server base","Minecraft Java","Minecraft Bedrock · Nukkit","Minecraft Bedrock · Official x86_64","Two OS choices","Media center","Network service stack")
    fun make(name:String):WbProject {
        if(name=="Empty canvas")return WbProject()
        var p=WbProject(name=name);val nodes=mutableListOf<WbNode>();val edges=mutableListOf<WbLink>()
        fun add(type:String,x:Float,y:Float)=WbCatalog.create(type,x,y).also{nodes+=it}
        val boot=add("boot.a",48f,32f);val os=add("os.pi64",48f,208f);val net=add("net.ethernet",48f,384f)
        edges+=WbLink(from=boot.id,to=os.id);edges+=WbLink(from=os.id,to=net.id)
        when(name){
            "Minecraft Java","Minecraft Bedrock · Nukkit"->{val java=add("runtime.java",344f,208f);val mc=add(if(name=="Minecraft Java")"mc.java" else "mc.nukkit",344f,384f);edges+=WbLink(from=os.id,to=java.id);edges+=WbLink(from=java.id,to=mc.id);edges+=WbLink(from=net.id,to=mc.id)}
            "Minecraft Bedrock · Official x86_64"->{p=p.copy(board="External x86_64 host",architecture="x86_64");nodes[1]=os.copy(type="os.ubuntu",name="Ubuntu (x86_64)",params=os.params+("architecture" to "x86_64"));val mc=add("mc.bedrock",344f,384f);edges+=WbLink(from=net.id,to=mc.id)}
            "Two OS choices"->{val b=add("os.ubuntu",344f,208f);val selector=add("boot.selector",344f,32f);edges+=WbLink(from=selector.id,to=os.id,relation=WbRelation.BEFORE);edges+=WbLink(from=selector.id,to=b.id,relation=WbRelation.BEFORE);p=p.copy(activeOsId=os.id)}
            "Media center"->{val m=add("server.media",344f,384f);edges+=WbLink(from=net.id,to=m.id)}
            "Network service stack"->{val ssh=add("service.ssh",344f,208f);val backup=add("service.backup",344f,384f);edges+=WbLink(from=net.id,to=ssh.id);edges+=WbLink(from=ssh.id,to=backup.id)}
            else->{val ssh=add("service.ssh",344f,384f);edges+=WbLink(from=net.id,to=ssh.id)}
        }
        return p.copy(nodes=nodes,links=edges)
    }
}
