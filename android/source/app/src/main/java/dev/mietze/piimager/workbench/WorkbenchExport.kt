package dev.mietze.piimager.workbench

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal object WbExport {
    private fun prop(value:String):String=buildString{value.forEachIndexed{i,c->append(when(c){'\\'->"\\\\";'\n'->"\\n";'\r'->"\\r";'\t'->"\\t";'='->"\\=";':'->"\\:";else->if((i==0&&c==' ')||c=='#'||c=='!')"\\$c" else if(c.code<32||c.code>126)"\\u%04x".format(Locale.ROOT,c.code) else c.toString()})}}
    fun properties(n:WbNode):String {
        require(n.type in setOf("mc.java","mc.bukkit","mc.nukkit","mc.bedrock"))
        require(WbCatalog.fieldErrors(n).isEmpty()){"Correct server properties before export"}
        val values=linkedMapOf<String,String>()
        WbCatalog.kind(n.type).fields.forEach{f->f.exportKey?.let{values[it]=n.value(f.key)}}
        if(n.type=="mc.nukkit"){
            values["gamemode"]=mapOf("survival" to "0","creative" to "1","adventure" to "2").getValue(n.value("mode"))
            values["difficulty"]=mapOf("peaceful" to "0","easy" to "1","normal" to "2","hard" to "3").getValue(n.value("difficulty"))
        }
        return "# Raspberry Manager · ${WbCatalog.kind(n.type).title}\n# Review against the selected server release. No server binary is included automatically.\n"+values.entries.joinToString("\n"){"${it.key}=${if(n.type=="mc.bedrock")it.value else prop(it.value)}"}+"\n"
    }
    fun files(p:WbProject):Map<String,String> {
        val a=WbRules.analyze(p)
        val files=linkedMapOf("project.rwb.json" to WbCodec.encode(p),"diagnostics.txt" to WbRules.report(p),"workspace.svg" to svg(p))
        val info=JSONObject().put("schema",1).put("project",p.name).put("targetArchitecture",p.architecture).put("executed",false)
            .put("diskWritten",false).put("bootVerified",false).put("errors",a.errors).put("order",JSONArray(a.order)).put("active",JSONArray(a.active.toList()))
        files["plan.json"]=info.toString(2)
        val readme=StringBuilder("""
Raspberry Manager · Pi Magic Workbench

This package is a portable project, static validation report and configuration bundle.
It is NOT a disk image, partitioner, installed MultiBoot loader or emulator.
No SSH commands, device writes, server downloads or server installations are performed by exporting.
Driver, OS, network and general-service blocks are explicit planning declarations in project.rwb.json.
The visual block order alone does not change firmware boot order.

Files:
project.rwb.json — editable project, including rules and attachment metadata.
plan.json — derived start order and validation state.
workspace.svg — diagram without external references or executable markup.
diagnostics.txt — errors, warnings and explicit verification limits.
servers/<block-id>/ — Minecraft configs and launch helpers for valid active profiles.
boot/ — reviewable fragments, never written to a live boot volume by this app.

Server setup (manual on your target Linux system):
1. Review diagnostics and choose versions matching your clients and Java runtime.
2. Obtain binaries from the referenced official source; accept required license terms yourself.
3. Put the JAR/binary in its matching servers/<id> directory (bedrock_server needs its full distribution).
4. Run `sh start.sh` in that directory. The script does not install packages or open firewall ports.
5. Optional user services assume this bundle is at ~/.local/share/raspberry-workbench/PROJECT_ID/.
   Copy desired .service files into ~/.config/systemd/user/ manually and review before enabling.
   No unit is enabled by generating this ZIP. Do not run game servers as root.

The Java EULA is false unless you explicitly accepted it in the editor.
Official Bedrock Linux binaries require an x86_64 supported Ubuntu host.
A Raspberry Pi ARM target is rejected; selecting a translation plan does not magically provide an emulator.
Nukkit is Java-based Bedrock server software. nukkit.org now describes legacy Nukkit;
Cloudburst Nukkit remains a separate implementation/source linked in this project.
Attachment paths from imported projects are never trusted: only local content-addressed copies can be exported.
Project notes and settings are visible to anyone you share this ZIP with.

""".trimIndent().replace("PROJECT_ID",p.id)).appendLine()
        if(a.errors>0)readme.appendLine("START HELPERS WITHHELD: fix ${a.errors} errors and re-export. Config files are review-only.")
        p.nodes.filter{it.id in a.active}.forEach{n->
            if(n.type in setOf("mc.java","mc.bukkit","mc.nukkit","mc.bedrock")&&WbCatalog.fieldErrors(n).isEmpty()){
                val dir="servers/${n.id}"
                files["$dir/server.properties"]=properties(n)
                if(n.type in listOf("mc.java","mc.bukkit"))files["$dir/eula.txt"]="# Read https://www.minecraft.net/eula\neula=${n.value("eula")}\n"
                files["$dir/PROVIDER.txt"]="${n.name}\n${WbCatalog.kind(n.type).website}\n"+(if(n.type=="mc.nukkit")"https://github.com/CloudburstMC/Nukkit\n" else "")+"Requested release: ${n.value("version")}\nJava major (where applicable): ${n.value("javaMajor")}\n"+"User source: ${n.value("sourceUrl")}\n"
                if(a.errors==0){
                    files["$dir/start.sh"]=launcher(n)
                    if(n.value("backup")=="true")files["$dir/backup-stopped-server.sh"]=backup(n)
                    if(n.value("autostart")=="true")files["systemd/raspberry-${n.id}.service"]=unit(p,n)
                }
            }
            if(n.type in setOf("mc.mod.forge","mc.mod.fabric")&&WbCatalog.fieldErrors(n).isEmpty()){
                val dir="clients/${n.id}"
                files["$dir/client-plan.properties"]=buildString { appendLine("# Raspberry Manager · planning file only"); appendLine("minecraft-version=${prop(n.value("minecraftVersion"))}"); appendLine("loader-version=${prop(n.value("loaderVersion"))}"); appendLine("java-major=${n.number("javaMajor")}"); appendLine("memory-mib=${n.number("memoryMiB")}"); appendLine("profile=${prop(n.value("profile"))}") }
                files["$dir/PROVIDER.txt"]="${n.name}\n${WbCatalog.kind(n.type).website}\nNo launcher, mod or account session is installed by this export.\n"
            }
            if(n.type=="boot.config"&&WbCatalog.fieldErrors(n).isEmpty()&&Regex("[A-Za-z0-9_.-]{1,100}").matches(n.value("key")))files["boot/${n.id}-config.txt.fragment"]="# Review only; not applied to an SD card\n[${n.value("section")}]\n${n.value("key")}=${n.value("value")}\n"
            if(n.type=="boot.cmdline"&&Regex("[A-Za-z0-9_.-]{1,100}").matches(n.value("key"))&&!n.value("value").any{it.isWhitespace()})files["boot/${n.id}-cmdline.token"]=n.value("key")+(if(n.value("value").isNotEmpty())"="+n.value("value") else "")+"\n"
        }
        files["README.txt"]=readme.toString()
        return files
    }
    fun launcher(n:WbNode):String {
        require(WbCatalog.fieldErrors(n).isEmpty())
        val head="""#!/bin/sh
# Raspberry Manager: run manually on the intended target, not inside Android.
set -eu
cd -- "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ "$(id -u)" = 0 ]; then echo 'Do not run this game server as root.' >&2; exit 1; fi
"""
        return if(n.type=="mc.bedrock")head+"""
case "$(uname -m)" in x86_64|amd64) ;; *) echo 'Official Bedrock requires x86_64. No ARM translation engine is bundled.' >&2; exit 1;; esac
[ -f ./bedrock_server ] || { echo 'Extract the official Linux Bedrock distribution here first.' >&2; exit 1; }
[ -x ./bedrock_server ] || { echo 'Review the downloaded executable and grant executable permission before starting.' >&2; exit 1; }
export LD_LIBRARY_PATH=".:${'$'}{LD_LIBRARY_PATH:-}"
exec ./bedrock_server
""" else head+"""
command -v java >/dev/null 2>&1 || { echo 'Install the matching Java runtime on this target first.' >&2; exit 1; }
major=$(java -XshowSettings:properties -version 2>&1 | awk '/java.specification.version =/{print ${'$'}3; exit}')
case "${'$'}major" in 1.*) major=${'$'}{major#1.};; esac
case "${'$'}major" in ''|*[!0-9]*) echo 'Could not verify Java version.' >&2; exit 1;; esac
[ "${'$'}major" -ge ${n.number("javaMajor")} ] || { echo 'The selected server needs a newer Java major.' >&2; exit 1; }
"""+(if(n.type in listOf("mc.java","mc.bukkit"))"grep -qx 'eula=true' eula.txt || { echo 'Read the Minecraft EULA and explicitly accept before starting.' >&2; exit 1; }\n" else "")+"""
[ -f ./${if(n.type=="mc.nukkit")"nukkit.jar" else "server.jar"} ] || { echo 'Place your verified server JAR in this directory first.' >&2; exit 1; }
exec java -Xms${n.number("minHeapMiB")}M -Xmx${n.number("memoryMiB")}M -jar ./${if(n.type=="mc.nukkit")"nukkit.jar" else "server.jar"}${if(n.type in listOf("mc.java","mc.bukkit"))" nogui" else ""}
"""
    }
    private fun unit(p:WbProject,n:WbNode):String="""
[Unit]
Description=Raspberry Manager game server ${n.id}
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=%h/.local/share/raspberry-workbench/${p.id}/servers/${n.id}
ExecStart=/bin/sh %h/.local/share/raspberry-workbench/${p.id}/servers/${n.id}/start.sh
Restart=${n.value("restart")}
RestartSec=10
NoNewPrivileges=true
UMask=0077

[Install]
WantedBy=default.target
""".trimIndent()+"\n"
    private fun backup(n:WbNode):String="""#!/bin/sh
# Manual backup helper. Stop the server cleanly before running this.
set -eu
cd -- "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
[ "${'$'}{1:-}" = '--server-is-stopped' ] || { echo 'Stop the server, then invoke with --server-is-stopped.' >&2; exit 1; }
umask 077
mkdir -p backups
out="backups/world-$(date -u +%Y%m%dT%H%M%SZ)-$$.tar.gz"
${if(n.type=="mc.bedrock"||n.type=="mc.nukkit")"[ -d worlds ] || { echo 'No worlds folder.' >&2; exit 1; }\ntar -czf \"${'$'}out\" -- worlds" else "[ -d '${n.value("world")}' ] || { echo 'No world folder.' >&2; exit 1; }\ntar -czf \"${'$'}out\" -- '${n.value("world")}'"}
printf 'Backup: %s\n' "${'$'}out"
"""
    fun svg(p:WbProject):String {
        fun xml(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
        val nodes=p.nodes;val l=(nodes.minOfOrNull{it.x}?:0f)-32;val t=(nodes.minOfOrNull{it.y}?:0f)-64
        val w=((nodes.maxOfOrNull{it.x}?:200f)+WB_NODE_W-l+32).coerceAtLeast(320f);val h=((nodes.maxOfOrNull{it.y}?:120f)+WB_NODE_H-t+32).coerceAtLeast(240f)
        return buildString{
            appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"$l $t $w $h\" role=\"img\">")
            appendLine("<title>${xml(p.name)}</title><rect x=\"$l\" y=\"$t\" width=\"$w\" height=\"$h\" fill=\"#09090d\"/>")
            appendLine("<text x=\"${l+24}\" y=\"${t+30}\" fill=\"#f5dce5\" font-family=\"sans-serif\" font-size=\"18\">${xml(p.name)} · Raspberry Manager</text>")
            p.links.forEach{e->val a=nodes.find{it.id==e.from};val b=nodes.find{it.id==e.to};if(a!=null&&b!=null){val ax=a.x+WB_NODE_W/2;val ay=a.y+WB_NODE_H;val bx=b.x+WB_NODE_W/2;val by=b.y
                appendLine("<path d=\"M $ax $ay C $ax ${ay+40} $bx ${by-40} $bx $by\" fill=\"none\" stroke=\"#d94372\" stroke-width=\"2\"/>")
                appendLine("<text x=\"${(ax+bx)/2+4}\" y=\"${(ay+by)/2}\" fill=\"#db90a7\" font-family=\"sans-serif\" font-size=\"10\">${xml(e.relation.label)}</text>")}}
            nodes.forEach{n->appendLine("<g><rect x=\"${n.x}\" y=\"${n.y}\" width=\"$WB_NODE_W\" height=\"$WB_NODE_H\" rx=\"18\" fill=\"#21151c\" stroke=\"#b72852\"/>")
                appendLine("<text x=\"${n.x+16}\" y=\"${n.y+28}\" fill=\"#d9819c\" font-family=\"sans-serif\" font-size=\"11\">${xml(WbCatalog.kind(n.type).category)}</text>")
                appendLine("<text x=\"${n.x+16}\" y=\"${n.y+56}\" fill=\"#ffffff\" font-family=\"sans-serif\" font-size=\"14\">${xml(n.name.take(24))}</text>")
                appendLine("<text x=\"${n.x+16}\" y=\"${n.y+83}\" fill=\"#cdbfc5\" font-family=\"sans-serif\" font-size=\"11\">${if(n.enabled)"Enabled" else "Disabled"} · ${n.number("reserveMiB")} MiB</text></g>")}
            appendLine("</svg>")
        }
    }
}
