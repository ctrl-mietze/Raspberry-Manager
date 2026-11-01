package dev.mietze.piimager.workbench

internal enum class WbSeverity { ERROR, WARNING, INFO }
internal data class WbFinding(val severity:WbSeverity,val code:String,val message:String,val node:String="")
internal data class WbAnalysis(val findings:List<WbFinding>,val order:List<String>,val active:Set<String>,val reservedMiB:Long,val memoryMiB:Long) {
    val errors get()=findings.count{it.severity==WbSeverity.ERROR}
    val warnings get()=findings.count{it.severity==WbSeverity.WARNING}
}
internal object WbRules {
    val conditionKeys=listOf("board","architecture","boot source","network","target OS")
    fun matches(p:WbProject,c:WbCondition):Boolean {
        val v=when(c.key){"board"->p.board;"architecture"->p.architecture;"boot source"->p.bootSource;"network"->p.network;"target OS"->p.targetOs;else->return false}
        return when(c.operator){"equals"->v.equals(c.value,true);"not equals"->!v.equals(c.value,true);else->false}
    }
    fun analyze(p:WbProject):WbAnalysis {
        val findings=mutableListOf<WbFinding>()
        fun report(s:WbSeverity,c:String,m:String,n:String=""){findings+=WbFinding(s,c,m,n)}
        val all=p.nodes.associateBy{it.id}
        val active=p.nodes.filter{it.enabled&&it.conditions.all{c->matches(p,c)}&&(!it.type.startsWith("os.")||p.activeOsId.isBlank()||it.id==p.activeOsId)}.map{it.id}.toSet()
        val actual=p.nodes.filter{it.id in active}
        val reserved=p.nodes.filter{it.enabled}.sumOf{it.number("reserveMiB").coerceAtLeast(0)}
        val memory=actual.sumOf{it.number("memoryMiB").coerceAtLeast(0)}
        if(p.nodes.isEmpty())report(WbSeverity.INFO,"EMPTY","Add a block or load a template. No OS image is downloaded.")
        if(p.activeOsId.isNotBlank()&&all[p.activeOsId]?.type?.startsWith("os.")!=true)report(WbSeverity.ERROR,"OS_SELECTION","The selected OS slot is missing.")
        if(actual.count{it.type.startsWith("os.")}>1)report(WbSeverity.ERROR,"OS_EXCLUSIVE","Several OS blocks are active. Choose one OS slot in Project settings. Adjacent blocks do not create a working MultiBoot selector.")
        if(actual.none{it.type.startsWith("os.")}&&actual.any{WbCatalog.kind(it.type).category !in listOf("Notes","Storage")})report(WbSeverity.WARNING,"NO_OS","No active OS declaration. Add the intended base system.")
        if(reserved>p.cardMiB)report(WbSeverity.ERROR,"STORAGE_OVERFLOW","Reservations exceed the planned card by ${reserved-p.cardMiB} MiB. Conditional OS slots still occupy storage.")
        if(memory>p.memoryMiB)report(WbSeverity.ERROR,"RAM_OVERFLOW","Active RAM budgets exceed the target by ${memory-p.memoryMiB} MiB.")
        actual.forEach{n->
            WbCatalog.fieldErrors(n).forEach{report(WbSeverity.ERROR,"PROPERTY",it,n.id)}
            val cat=WbCatalog.kind(n.type).category
            if(n.attachment!=null&&n.value("sha256").isNotBlank()&&!n.value("sha256").equals(n.attachment.sha256,true))report(WbSeverity.ERROR,"FILE_HASH","Attached file does not match the expected SHA-256.",n.id)
            if(n.type=="minecraft")report(WbSeverity.ERROR,"MC_CHOICE","Choose a Minecraft Bedrock or Java profile from the tree.",n.id)
            if(n.type.startsWith("mc.")){
                if(p.targetOs!="linux")report(WbSeverity.ERROR,"MC_OS","The generated launch bundle targets Linux, not ${p.targetOs}.",n.id)
                if(actual.none{it.type in listOf("net.ethernet","net.wifi")})report(WbSeverity.WARNING,"NO_NETWORK_BLOCK","Add a Wi-Fi or Ethernet declaration for this server.",n.id)
                if(p.network=="offline")report(WbSeverity.WARNING,"OFFLINE","The project environment is offline; multiplayer access/downloads are not validated.",n.id)
                if(n.type in listOf("mc.java","mc.bukkit","mc.nukkit","mc.mod.forge","mc.mod.fabric")){
                    val required=n.number("javaMajor")
                    if(actual.none{it.type=="runtime.java"&&it.number("major")>=required})report(WbSeverity.ERROR,"JAVA_RUNTIME","Add a Java runtime with major ≥ $required.",n.id)
                    if(n.number("minHeapMiB")>n.number("memoryMiB"))report(WbSeverity.ERROR,"HEAP","Initial Java heap exceeds maximum heap.",n.id)
                    if(n.type in listOf("mc.java","mc.bukkit")&&n.value("eula")!="true")report(WbSeverity.WARNING,"EULA","Minecraft EULA is not accepted. The launcher will refuse to start until you explicitly accept it.",n.id)
                }
                if(n.type=="mc.nukkit")report(WbSeverity.INFO,"NUKKIT","Nukkit is a Bedrock implementation written in Java. Plugin/protocol compatibility depends on the build; nukkit.org now describes legacy Nukkit and a successor.",n.id)
                if(n.type in listOf("mc.mod.forge","mc.mod.fabric"))report(WbSeverity.INFO,"MC_CLIENT_PLAN","Mod Client is a configuration plan only; Raspberry Manager does not install a launcher, mods or a Minecraft account session.",n.id)
                if(n.type=="mc.bedrock"&&p.architecture!="x86_64")report(WbSeverity.ERROR,"BEDROCK_ARM","Normal Bedrock's official Linux binary is x86_64, not native Pi ARM. This editor does not supply or validate an ARM translation layer. Choose an x86_64 target or use Nukkit.",n.id)
                if(n.type=="mc.bedrock")report(WbSeverity.WARNING,"BEDROCK_DISTRO","Official Bedrock requires a supported Ubuntu release. Verify the selected binary and OS; a Linux label alone does not prove compatibility.",n.id)
                if(n.type=="mc.bedrock"&&n.value("execution")!="native x86_64")report(WbSeverity.WARNING,"REMOTE_DECLARATION","External hosts / translation are planning declarations, not deployed or emulated here.",n.id)
                if(n.value("online")=="false")report(WbSeverity.WARNING,"AUTH_DISABLED","Server authentication is disabled in this configuration.",n.id)
            }
            if(cat=="Drivers"&&n.value("kernel").isBlank())report(WbSeverity.WARNING,"DRIVER_KERNEL","Specify a kernel release before relying on this driver.",n.id)
            if(cat in listOf("Drivers","Boot")&&n.attachment==null&&n.value("sourceUrl").isBlank()&&n.type !in listOf("boot.config","boot.cmdline","boot.selector"))report(WbSeverity.INFO,"ARTIFACT_MISSING","This block is a declaration; no local file is attached.",n.id)
            if(n.type=="boot.selector")report(WbSeverity.WARNING,"BOOT_PLANNER","This is a selector plan, not an installed MultiBoot implementation.",n.id)
            if(n.type.startsWith("os.")&&n.value("architecture")!=p.architecture)report(WbSeverity.ERROR,"ARCHITECTURE","Image architecture ${n.value("architecture")} differs from target ${p.architecture}.",n.id)
            if(n.type=="os.android"&&p.targetOs!="android")report(WbSeverity.ERROR,"ANDROID_TARGET","An Android OS block needs target OS = android.",n.id)
            if(cat=="Services")report(WbSeverity.INFO,"SERVICE_PLAN","${n.name} is exported as a service declaration, not an installer.",n.id)
            if(n.type=="boot.config"||n.type=="boot.cmdline"){
                if(!Regex("[A-Za-z0-9_.-]{1,100}").matches(n.value("key")))report(WbSeverity.ERROR,"BOOT_KEY","Boot key contains unsupported characters.",n.id)
                if(n.value("value").any{it=='\n'||it=='\r'||it=='\u0000'})report(WbSeverity.ERROR,"BOOT_VALUE","Boot values must be single-line.",n.id)
            }
        }
        val directed=mutableListOf<Pair<String,String>>()
        p.links.forEach{e->
            if(e.from !in all||e.to !in all)report(WbSeverity.ERROR,"DANGLING","Connection references a missing block.")
            else if(e.from==e.to)report(WbSeverity.ERROR,"SELF_LINK","A block cannot depend on itself.",e.from)
            else when(e.relation){
                WbRelation.REQUIRES->{if(e.to in active&&e.from !in active)report(WbSeverity.ERROR,"INACTIVE_REQUIRED","${all[e.to]?.name} requires inactive ${all[e.from]?.name}.",e.to);if(e.from in active&&e.to in active)directed+=e.from to e.to}
                WbRelation.BEFORE,WbRelation.OPTIONAL->if(e.from in active&&e.to in active)directed+=e.from to e.to
                WbRelation.AFTER->if(e.from in active&&e.to in active)directed+=e.to to e.from
                WbRelation.CONFLICT->if(e.from in active&&e.to in active)report(WbSeverity.ERROR,"CONFLICT","${all[e.from]?.name} conflicts with ${all[e.to]?.name}.",e.from)
                WbRelation.FALLBACK->if(e.from in active&&e.to in active)report(WbSeverity.INFO,"FALLBACK","${all[e.to]?.name} is a simulated fallback for ${all[e.from]?.name}.",e.to)
            }
        }
        val adjacency=directed.distinct().groupBy{it.first}.mapValues{it.value.map{p->p.second}}
        val degree=active.associateWith{0}.toMutableMap();directed.distinct().forEach{degree[it.second]=degree.getValue(it.second)+1}
        val queue=java.util.PriorityQueue<String>(compareBy<String>{all.getValue(it).priority}.thenBy{all.getValue(it).name}.thenBy{it})
        degree.filterValues{it==0}.keys.forEach(queue::add);val order=mutableListOf<String>()
        while(queue.isNotEmpty()){val id=queue.remove();order+=id;adjacency[id].orEmpty().forEach{v->degree[v]=degree.getValue(v)-1;if(degree[v]==0)queue.add(v)}}
        if(order.size<active.size)report(WbSeverity.ERROR,"CYCLE","Start-order dependencies contain a cycle. Remove or change the marked relations.")
        val fallbackPairs=p.links.filter{it.relation==WbRelation.FALLBACK}.map{it.from to it.to}
        if(hasCycle(fallbackPairs))report(WbSeverity.ERROR,"FALLBACK_CYCLE","Fallback relations form a cycle.")
        val sockets=mutableMapOf<Pair<String,Long>,MutableSet<String>>()
        actual.filter{it.type in setOf("mc.java","mc.bukkit","mc.nukkit","mc.bedrock")||WbCatalog.kind(it.type).category=="Services"}.forEach{n->
            val protocol=if(n.type in listOf("mc.bedrock","mc.nukkit"))"udp" else if(n.type in listOf("mc.java","mc.bukkit"))"tcp" else n.value("protocol")
            val ports=mutableSetOf(n.number("port"));if(n.type=="mc.bedrock"){ports+=n.number("portV6");if(n.value("lanVisibility")=="true"){ports+=19132L;ports+=19133L}}
            ports.filter{it>0}.forEach{sockets.getOrPut(protocol to it){mutableSetOf()}.add(n.id)}
        }
        sockets.filterValues{it.size>1}.forEach{(socket,ids)->report(WbSeverity.ERROR,"PORT_CONFLICT","${socket.first.uppercase()} ${socket.second} is used by ${ids.map{all[it]?.name}.joinToString()}.")}
        findings+=WbFinding(WbSeverity.INFO,"SCOPE","Static plan checks, not real firmware emulation or a successful hardware boot test.")
        return WbAnalysis(findings.distinct(),order,active,reserved,memory)
    }
    private fun hasCycle(pairs:List<Pair<String,String>>):Boolean {
        val nodes=pairs.flatMap{listOf(it.first,it.second)}.toSet();val visited=mutableSetOf<String>();val path=mutableSetOf<String>();val map=pairs.groupBy{it.first}
        fun visit(id:String):Boolean{if(id in path)return true;if(!visited.add(id))return false;path+=id;for(e in map[id].orEmpty())if(visit(e.second))return true;path-=id;return false}
        return nodes.any{visit(it)}
    }
    fun simulate(p:WbProject,failed:Set<String>):List<String>{
        val a=analyze(p);val all=p.nodes.associateBy{it.id};val log=mutableListOf("EMULATELOAD · static scenario · no process or OS started")
        if(a.errors>0){log+="Blocked by ${a.errors} validation errors.";return log+a.findings.filter{it.severity==WbSeverity.ERROR}.map{it.message}}
        val standby=p.links.filter{it.relation==WbRelation.FALLBACK}.map{it.to}.toSet();val done=mutableSetOf<String>();val bad=mutableSetOf<String>()
        val settled=mutableSetOf<String>()
        val fallbackParents=p.links.filter{it.relation==WbRelation.FALLBACK}.groupBy{it.to}
        val prerequisites=p.links.filter{it.relation==WbRelation.REQUIRES}.groupBy{it.to}
        // Revisit pending nodes so a fallback can satisfy dependencies. Never
        // start a dependent simply because its prerequisite has not failed yet.
        repeat((p.nodes.size+1).coerceAtMost(WB_MAX_NODES+1)){
            for(id in a.order){
                if(id in settled)continue
                val fallback=id in standby
                if(fallback&&fallbackParents[id].orEmpty().none{it.from in bad})continue
                val needed=prerequisites[id].orEmpty().map{it.from}
                if(needed.any{it in bad}){bad+=id;settled+=id;log+="BLOCKED · ${all[id]?.name}: required block failed"}
                else if(needed.any{it !in done})continue
                else if(id in failed){bad+=id;settled+=id;log+="${if(fallback)"SIMULATED FALLBACK FAILURE"else"SIMULATED FAILURE"} · ${all[id]?.name}"}
                else{done+=id;settled+=id;log+="${if(fallback)"PLANNED FALLBACK"else"PLANNED START"} · ${all[id]?.name}"}
            }
        }
        (a.active-settled).forEach{id->log+="${if(id in standby)"STANDBY"else"NOT STARTED"} · ${all[id]?.name}: activation or prerequisite not reached"}
        log+="Finished: ${done.size} planned starts, ${bad.size} simulated failures/blocks. Hardware remains unverified."
        return log
    }
    fun report(p:WbProject):String{val a=analyze(p);return buildString{
        appendLine("Raspberry Manager · Pi Magic Workbench");appendLine(p.name);appendLine("Target: ${p.board} / ${p.architecture} / ${p.targetOs}")
        appendLine("Storage reserved: ${a.reservedMiB}/${p.cardMiB} MiB; vacuum: ${(p.cardMiB-a.reservedMiB).coerceAtLeast(0)} MiB")
        appendLine("RAM budgets: ${a.memoryMiB}/${p.memoryMiB} MiB");appendLine("${a.errors} errors · ${a.warnings} warnings")
        a.findings.forEach{appendLine("${it.severity} [${it.code}] ${it.message}")}
        appendLine("\nPlanned start order (NOT a hardware boot result):");a.order.forEachIndexed{i,id->appendLine("${i+1}. ${p.nodes.find{it.id==id}?.name}")}
    }}
}
