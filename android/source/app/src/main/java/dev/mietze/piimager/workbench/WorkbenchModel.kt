package dev.mietze.piimager.workbench

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import kotlin.math.*

internal const val WB_MAX_NODES = 256
internal const val WB_MAX_LINKS = 1024
internal const val WB_NODE_W = 212f
internal const val WB_NODE_H = 112f
internal fun newWbId(): String = UUID.randomUUID().toString()
internal data class WbCondition(val key: String = "network", val operator: String = "equals", val value: String = "online")
internal enum class WbRelation(val label: String, val explanation: String) {
    REQUIRES("Requires", "A → B: B requires A. A must run first."),
    BEFORE("Before", "A → B: schedule A before B."),
    AFTER("After", "A → B: schedule A after B."),
    OPTIONAL("Optional", "A → B: use A before B when both are active."),
    CONFLICT("Conflicts", "A and B must not be active together."),
    FALLBACK("Fallback", "A → B: try B when A fails. Not a normal start-order edge.")
}
internal data class WbLink(val id: String = newWbId(), val from: String, val to: String, val relation: WbRelation = WbRelation.REQUIRES)
internal data class WbAttachment(val name: String, val sha256: String, val bytes: Long, val format: String)
internal data class WbNode(
    val id: String = newWbId(), val type: String, val name: String, val x: Float = 48f, val y: Float = 48f,
    val enabled: Boolean = true, val optional: Boolean = false, val locked: Boolean = false, val group: String = "",
    val notes: String = "", val priority: Int = 50, val params: Map<String, String> = emptyMap(),
    val conditions: List<WbCondition> = emptyList(), val attachment: WbAttachment? = null
) {
    fun value(key: String): String = params[key] ?: WbCatalog.kind(type).fields.find { it.key == key }?.default.orEmpty()
    fun number(key: String): Long = value(key).toLongOrNull() ?: 0L
}
internal data class WbProject(
    val id: String = newWbId(), val name: String = "My Raspberry workspace", val board: String = "Pi 5",
    val architecture: String = "arm64", val cardMiB: Long = 65536, val memoryMiB: Long = 8192,
    val bootSource: String = "sd", val network: String = "online", val targetOs: String = "linux",
    val activeOsId: String = "", val grid: Boolean = true, val snap: Boolean = true, val minimap: Boolean = true,
    val nodes: List<WbNode> = emptyList(), val links: List<WbLink> = emptyList()
)
internal data class WbView(val zoom: Float = 1f, val panX: Float = 24f, val panY: Float = 24f)
internal object WbGeometry {
    fun world(sx: Float, sy: Float, v: WbView): Pair<Float, Float> = (sx-v.panX)/v.zoom to (sy-v.panY)/v.zoom
    fun screen(x: Float, y: Float, v: WbView): Pair<Float, Float> = x*v.zoom+v.panX to y*v.zoom+v.panY
    fun zoom(v: WbView, factor: Float, cx: Float, cy: Float): WbView {
        if (!factor.isFinite() || factor <= 0) return v
        val z=(v.zoom*factor).coerceIn(.001f,2.5f); val ratio=z/v.zoom
        return WbView(z,(cx-(cx-v.panX)*ratio).coerceIn(-50000f,50000f),(cy-(cy-v.panY)*ratio).coerceIn(-50000f,50000f))
    }
    fun hit(nodes: List<WbNode>, x: Float, y: Float): WbNode? = nodes.lastOrNull { x>=it.x && x<=it.x+WB_NODE_W && y>=it.y && y<=it.y+WB_NODE_H }
    fun fit(nodes: List<WbNode>, width: Float, height: Float): WbView {
        if (nodes.isEmpty()) return WbView()
        val l=nodes.minOf{it.x};val t=nodes.minOf{it.y};val w=nodes.maxOf{it.x}+WB_NODE_W-l;val h=nodes.maxOf{it.y}+WB_NODE_H-t
        val z=min((width-48).coerceAtLeast(80f)/w,(height-48).coerceAtLeast(80f)/h).coerceIn(.001f,1.4f)
        return WbView(z,(width-w*z)/2-l*z,(height-h*z)/2-t*z)
    }
    fun snap(v: Float, enabled: Boolean): Float = (if (enabled) (v/16f).roundToInt()*16f else v).coerceIn(-20000f,20000f)
}

/** Strict, bounded interchange. Never reads arbitrary paths or executes imported instructions. */
internal object WbCodec {
    const val MAX_BYTES=2*1024*1024
    private val idPattern=Regex("[A-Za-z0-9_-]{1,80}")
    private fun ident(s:String):String { require(idPattern.matches(s)){"Invalid document identifier"};return s }
    private fun text(s:String,max:Int):String { require(s.length<=max && '\u0000' !in s){"Text exceeds bounds"};return s }
    private fun coordinate(o:JSONObject,k:String):Float { val d=o.getDouble(k);require(d.isFinite() && abs(d)<=20000){"Invalid canvas position"};return d.toFloat() }
    private fun num(o:JSONObject,k:String,min:Long,max:Long):Long {val raw=o.get(k);require(raw is Number && raw.toDouble().isFinite() && raw.toDouble()%1.0==0.0){"Expected whole number for $k"};val n=raw.toLong();require(n in min..max){"Invalid $k"};return n}
    fun encode(p:WbProject):String = JSONObject().apply {
        put("format","raspberry-workbench");put("schema",1);put("id",p.id);put("name",p.name);put("board",p.board)
        put("architecture",p.architecture);put("cardMiB",p.cardMiB);put("memoryMiB",p.memoryMiB)
        put("bootSource",p.bootSource);put("network",p.network);put("targetOs",p.targetOs);put("activeOsId",p.activeOsId)
        put("grid",p.grid);put("snap",p.snap);put("minimap",p.minimap)
        put("nodes",JSONArray().apply {p.nodes.forEach {n->put(JSONObject().apply {
            put("id",n.id);put("type",n.type);put("name",n.name);put("x",n.x.toDouble());put("y",n.y.toDouble())
            put("enabled",n.enabled);put("optional",n.optional);put("locked",n.locked);put("group",n.group)
            put("notes",n.notes);put("priority",n.priority);put("params",JSONObject(n.params))
            put("conditions",JSONArray().apply{n.conditions.forEach{put(JSONObject().put("key",it.key).put("operator",it.operator).put("value",it.value))}})
            n.attachment?.let{put("attachment",JSONObject().put("name",it.name).put("sha256",it.sha256).put("bytes",it.bytes).put("format",it.format))}
        })}})
        put("links",JSONArray().apply{p.links.forEach{put(JSONObject().put("id",it.id).put("from",it.from).put("to",it.to).put("relation",it.relation.name))}})
    }.toString(2)
    fun decode(raw:String):WbProject {
        require(raw.toByteArray().size<=MAX_BYTES){"Project exceeds 2 MiB"}
        val o=JSONObject(raw);require(o.getString("format")=="raspberry-workbench" && o.getInt("schema")==1){"Unsupported workbench schema"}
        val arr=o.getJSONArray("nodes");require(arr.length()<=WB_MAX_NODES){"Too many blocks"}
        val nodes=(0 until arr.length()).map{i->val n=arr.getJSONObject(i);val type=n.getString("type");require(WbCatalog.contains(type)){"Unknown block type: $type"}
            val values=n.getJSONObject("params");require(values.length()<=160){"Too many properties"}
            val params=values.keys().asSequence().associateWith{key->require(Regex("[a-zA-Z0-9_.-]{1,80}").matches(key));text(values.getString(key),2048)}
            val cond=n.optJSONArray("conditions")?:JSONArray();require(cond.length()<=16){"Too many conditions"}
            val conditions=(0 until cond.length()).map{j->val c=cond.getJSONObject(j);val key=c.getString("key");val op=c.getString("operator")
                require(key in WbRules.conditionKeys && op in listOf("equals","not equals")){"Unsupported condition"}
                WbCondition(key,op,text(c.getString("value"),100))}
            val a=n.optJSONObject("attachment")?.let{a->val hash=a.getString("sha256");require(Regex("[0-9a-f]{64}").matches(hash)){"Invalid file hash"}
                WbAttachment(text(a.getString("name"),160),hash,num(a,"bytes",0,64L*1024*1024),text(a.getString("format"),40))}
            WbNode(ident(n.getString("id")),type,text(n.getString("name"),100),coordinate(n,"x"),coordinate(n,"y"),n.optBoolean("enabled",true),n.optBoolean("optional"),n.optBoolean("locked"),text(n.optString("group"),80),text(n.optString("notes"),4096),num(n,"priority",0,999).toInt(),params,conditions,a)
        }
        require(nodes.map{it.id}.toSet().size==nodes.size){"Duplicate block IDs"}
        val links=o.getJSONArray("links");require(links.length()<=WB_MAX_LINKS){"Too many connections"}
        val edges=(0 until links.length()).map{i->val e=links.getJSONObject(i);WbLink(ident(e.getString("id")),ident(e.getString("from")),ident(e.getString("to")),WbRelation.valueOf(e.getString("relation")))}
        require(edges.map{it.id}.toSet().size==edges.size){"Duplicate connection IDs"}
        val arch=o.getString("architecture");require(arch in listOf("arm64","arm32","x86_64"))
        val boot=o.getString("bootSource");require(boot in listOf("sd","usb","nvme","network"))
        val network=o.getString("network");require(network in listOf("online","offline"))
        val os=o.getString("targetOs");require(os in listOf("linux","android","other"))
        return WbProject(ident(o.getString("id")),text(o.getString("name"),120),text(o.getString("board"),80),arch,num(o,"cardMiB",512,8L*1024*1024),num(o,"memoryMiB",128,1024*1024),boot,network,os,text(o.optString("activeOsId"),80),o.optBoolean("grid",true),o.optBoolean("snap",true),o.optBoolean("minimap",true),nodes,edges)
    }
    /** Imports are new documents; they cannot overwrite another local project ID. */
    fun asCopy(p:WbProject):WbProject=p.copy(id=newWbId(),name=(p.name+" copy").take(120))
    fun safeHttps(s:String):Boolean=runCatching{val u=URI(s);u.scheme=="https"&&!u.host.isNullOrBlank()&&u.rawUserInfo==null&&u.port in setOf(-1,443)&&!s.any{it.isWhitespace()}}.getOrDefault(false)
}

internal object WbEdits {
    fun remove(p:WbProject,ids:Set<String>):WbProject=p.copy(nodes=p.nodes.filterNot{it.id in ids},links=p.links.filterNot{it.from in ids||it.to in ids},activeOsId=p.activeOsId.takeUnless{it in ids}.orEmpty())
    fun duplicate(p:WbProject,ids:Set<String>):WbProject {
        val originals=p.nodes.filter{it.id in ids};require(p.nodes.size+originals.size<=WB_MAX_NODES){"256 block limit"}
        val map=originals.associate{it.id to newWbId()}
        val copies=originals.map{it.copy(id=map.getValue(it.id),name=(it.name+" copy").take(100),x=(it.x+32).coerceAtMost(20000f),y=(it.y+32).coerceAtMost(20000f),locked=false)}
        val extra=p.links.filter{it.from in ids&&it.to in ids}.map{it.copy(id=newWbId(),from=map.getValue(it.from),to=map.getValue(it.to))}
        require(p.links.size+extra.size<=WB_MAX_LINKS)
        return p.copy(nodes=p.nodes+copies,links=p.links+extra)
    }
    fun arrange(p:WbProject,ids:Set<String>,mode:String):WbProject {
        val chosen=p.nodes.filter{it.id in ids&&!it.locked}.sortedWith(compareBy<WbNode>{it.y}.thenBy{it.x})
        if(chosen.isEmpty())return p
        val l=chosen.minOf{it.x};val t=chosen.minOf{it.y};val positions=chosen.mapIndexed{i,n->n.id to when(mode){
            "row"->l+i*(WB_NODE_W+48) to t;"column"->l to t+i*(WB_NODE_H+48)
            "left"->l to n.y;"top"->n.x to t
            "right"->chosen.maxOf{it.x} to n.y;"bottom"->n.x to chosen.maxOf{it.y}
            "spread_x"-> {
                val seq=chosen.sortedBy{it.x};val order=seq.indexOfFirst{it.id==n.id}
                val step=maxOf(WB_NODE_W+40f,(seq.maxOf{it.x}-l)/(seq.size-1).coerceAtLeast(1))
                (l+order*step) to n.y
            }
            "spread_y"-> {
                val seq=chosen.sortedBy{it.y};val order=seq.indexOfFirst{it.id==n.id}
                val step=maxOf(WB_NODE_H+40f,(seq.maxOf{it.y}-t)/(seq.size-1).coerceAtLeast(1))
                n.x to (t+order*step)
            }
            else->l+(i%3)*(WB_NODE_W+48) to t+(i/3)*(WB_NODE_H+48)
        }}.toMap()
        return p.copy(nodes=p.nodes.map{n->positions[n.id]?.let{n.copy(x=it.first.coerceIn(-20000f,20000f),y=it.second.coerceIn(-20000f,20000f))}?:n})
    }
    fun wireVertical(p:WbProject,ids:Set<String>):WbProject {
        val chosen=p.nodes.filter{it.id in ids}.sortedBy{it.y}
        val extra=chosen.zipWithNext().filter{(a,b)->p.links.none{it.from==a.id&&it.to==b.id&&it.relation==WbRelation.BEFORE}}.map{(a,b)->WbLink(from=a.id,to=b.id,relation=WbRelation.BEFORE)}
        require(p.links.size+extra.size<=WB_MAX_LINKS)
        return p.copy(links=p.links+extra)
    }
}
