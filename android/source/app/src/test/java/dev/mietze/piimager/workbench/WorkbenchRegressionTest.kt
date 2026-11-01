package dev.mietze.piimager.workbench

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File

class WorkbenchRegressionTest {
    private fun node(id:String,type:String="note")=WbCatalog.create(type).copy(id=id,name=id)
    private fun edge(a:String,b:String,kind:WbRelation=WbRelation.REQUIRES)=WbLink(from=a,to=b,relation=kind)
    @Test fun untriggeredStandbyCannotSatisfyDependency(){
        val p=WbProject(nodes=listOf(node("primary"),node("standby"),node("service")),links=listOf(edge("primary","standby",WbRelation.FALLBACK),edge("standby","service")))
        val logs=WbRules.simulate(p,emptySet());assertFalse(logs.contains("PLANNED START · service"));assertTrue(logs.any{it.startsWith("NOT STARTED · service")})
    }
    @Test fun fallbackMustRespectFailedPrerequisite(){
        val p=WbProject(nodes=listOf(node("primary"),node("standby"),node("required")),links=listOf(edge("primary","standby",WbRelation.FALLBACK),edge("required","standby")))
        val logs=WbRules.simulate(p,setOf("primary","required"));assertFalse(logs.contains("PLANNED FALLBACK · standby"));assertTrue(logs.any{it.startsWith("BLOCKED · standby")})
    }
    @Test fun activatedFallbackCanUnblockFollowingService(){
        val p=WbProject(nodes=listOf(node("primary"),node("standby"),node("service")),links=listOf(edge("primary","standby",WbRelation.FALLBACK),edge("standby","service")))
        val logs=WbRules.simulate(p,setOf("primary"));assertTrue(logs.contains("PLANNED FALLBACK · standby"));assertTrue(logs.contains("PLANNED START · service"))
        assertTrue(logs.indexOf("PLANNED FALLBACK · standby")<logs.indexOf("PLANNED START · service"))
    }
    @Test fun fallbackFailureCanTriggerAnotherFallback(){
        val p=WbProject(nodes=listOf(node("a"),node("b"),node("c")),links=listOf(edge("a","b",WbRelation.FALLBACK),edge("b","c",WbRelation.FALLBACK)))
        val logs=WbRules.simulate(p,setOf("a","b"));assertTrue(logs.contains("PLANNED FALLBACK · c"))
    }
    @Test fun expectedFileHashIsChecked(){
        val n=node("driver","driver.wifi").copy(params=mapOf("sha256" to "a".repeat(64)),attachment=WbAttachment("driver.ko","b".repeat(64),10,"ELF"))
        assertTrue(WbRules.analyze(WbProject(nodes=listOf(n))).findings.any{it.code=="FILE_HASH"})
    }
    @Test fun expectedHashAllowsCase(){
        val n=node("driver","driver.wifi").copy(params=mapOf("sha256" to "A".repeat(64)),attachment=WbAttachment("driver.ko","a".repeat(64),10,"ELF"))
        assertFalse(WbRules.analyze(WbProject(nodes=listOf(n))).findings.any{it.code=="FILE_HASH"})
    }
    @Test fun nativeBedrockDoesNotUseJavaUnicodeEscapes(){
        val n=node("mc","mc.bedrock").let{it.copy(params=it.params+("serverName" to "Mietze ö : ="))}
        assertTrue(WbExport.properties(n).contains("server-name=Mietze ö : =\n"))
    }
    @Test fun entireValidCanvasCanBeFitted(){
        val nodes=listOf(node("a").copy(x=-20000f,y=-20000f),node("b").copy(x=20000f,y=20000f))
        val view=WbGeometry.fit(nodes,320f,480f)
        nodes.forEach{val v=WbGeometry.screen(it.x,it.y,view);assertTrue(v.first>=0&&v.second>=0);assertTrue(v.first+WB_NODE_W*view.zoom<=321);assertTrue(v.second+WB_NODE_H*view.zoom<=481)}
    }
    @Test fun fractionalStorageCapacityIsRejected(){
        val json=JSONObject(WbCodec.encode(WbProject())).put("cardMiB",5000.25)
        try{WbCodec.decode(json.toString());fail("Fractions must not be rounded silently")}catch(_:IllegalArgumentException){}
    }
    @Test fun generateRealExampleBundlesForSyntaxReview(){
        val out=System.getenv("WB_TEST_EXPORT_DIR")?.let{File(it)}?:return
        out.mkdirs()
        WbTemplates.names.forEachIndexed{i,name->
            val p=WbTemplates.make(name);val folder=File(out,"template-$i").apply{mkdirs()}
            WbExport.files(p).forEach{(relative,body)->File(folder,relative).also{it.parentFile?.mkdirs();it.writeText(body)}}
        }
        val p=WbTemplates.make("Minecraft Java").let{it.copy(nodes=it.nodes.map{n->if(n.type=="mc.java")n.copy(params=n.params+("autostart" to "true"))else n})}
        WbExport.files(p).forEach{(relative,body)->File(out,"user-service/$relative").also{it.parentFile?.mkdirs();it.writeText(body)}}
        File(out,"counts.json").writeText(JSONObject().put("blockTypes",WbCatalog.kinds.size).put("propertySlots",WbCatalog.kinds.sumOf{it.fields.size}).put("editorCommands",WbTool.entries.size).put("templates",WbTemplates.names.size).toString(2))
    }
}
