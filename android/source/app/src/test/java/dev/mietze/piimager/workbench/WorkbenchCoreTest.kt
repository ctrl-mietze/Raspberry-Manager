package dev.mietze.piimager.workbench

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

class WorkbenchCoreTest {
    private fun n(type:String="note",id:String=newWbId(),x:Float=0f,y:Float=0f)=WbCatalog.create(type,x,y).copy(id=id)
    private fun errors(p:WbProject)=WbRules.analyze(p).findings.filter{it.severity==WbSeverity.ERROR}.map{it.code}
    private fun reject(block:()->Unit){try{block();fail("Expected rejection")}catch(e:IllegalArgumentException){}}
    @Test fun blankHasNoRecovery(){assertTrue(WbProject().nodes.isEmpty())}
    @Test fun allDefaultsValidate(){WbCatalog.kinds.forEach{assertEquals(it.id,emptyList<String>(),WbCatalog.fieldErrors(n(it.id)))}}
    @Test fun catalogKeysAreUnique(){assertEquals(WbCatalog.kinds.size,WbCatalog.kinds.map{it.id}.toSet().size);WbCatalog.kinds.forEach{assertEquals(it.id,it.fields.size,it.fields.map{f->f.key}.toSet().size)}}
    @Test fun allKindsRoundTrip(){val p=WbProject(nodes=WbCatalog.kinds.map{n(it.id)});assertEquals(p,WbCodec.decode(WbCodec.encode(p)))}
    @Test fun documentRoundTripIncludesLinksFilesConditions(){val a=n("note","a").copy(conditions=listOf(WbCondition()),attachment=WbAttachment("file.jar","a".repeat(64),10,"JAR/ZIP"));val b=n("note","b");val p=WbProject(nodes=listOf(a,b),links=listOf(WbLink(from=a.id,to=b.id)));assertEquals(p,WbCodec.decode(WbCodec.encode(p)))}
    @Test fun copiedDocumentHasNewIdentity(){val p=WbProject();val q=WbCodec.asCopy(p);assertNotEquals(p.id,q.id);assertEquals(p.nodes,q.nodes)}
    @Test fun duplicateIdsRejected(){val a=n(id="a");reject{WbCodec.decode(WbCodec.encode(WbProject(nodes=listOf(a,a))))}}
    @Test fun traversalIdRejected(){val p=WbProject(id="../escape");reject{WbCodec.decode(WbCodec.encode(p))}}
    @Test fun oversizedDocumentRejected(){reject{WbCodec.decode(" ".repeat(WbCodec.MAX_BYTES+1))}}
    @Test fun tooManyNodesRejected(){reject{WbCodec.decode(WbCodec.encode(WbProject(nodes=(0..WB_MAX_NODES).map{n(id="n$it")})))}}
    @Test fun unsupportedSchemaRejected(){val o=JSONObject(WbCodec.encode(WbProject())).put("schema",999);reject{WbCodec.decode(o.toString())}}
    @Test fun extremeCoordinatesRejected(){reject{WbCodec.decode(WbCodec.encode(WbProject(nodes=listOf(n().copy(x=50000f)))) )}}
    @Test fun unknownConditionRejected(){val p=WbProject(nodes=listOf(n().copy(conditions=listOf(WbCondition("execute","equals","rm")))));reject{WbCodec.decode(WbCodec.encode(p))}}
    @Test fun unknownTypeRejected(){reject{WbCodec.decode(WbCodec.encode(WbProject(nodes=listOf(n().copy(type="arbitrary-script")))) )}}
    @Test fun invalidHashRejected(){val p=WbProject(nodes=listOf(n().copy(attachment=WbAttachment("f","../abc",0,"file"))));reject{WbCodec.decode(WbCodec.encode(p))}}
    @Test fun linkWithoutEndpointReported(){val p=WbProject(nodes=listOf(n(id="a")),links=listOf(WbLink(from="a",to="missing")));assertTrue("DANGLING" in errors(p))}
    @Test fun selfLinkRejectedByValidator(){val p=WbProject(nodes=listOf(n(id="a")),links=listOf(WbLink(from="a",to="a")));assertTrue("SELF_LINK" in errors(p))}
    @Test fun requiredOrderIsTopological(){val p=WbProject(nodes=listOf(n(id="b"),n(id="a")),links=listOf(WbLink(from="a",to="b")));assertEquals(listOf("a","b"),WbRules.analyze(p).order)}
    @Test fun afterReversesOrder(){val p=WbProject(nodes=listOf(n(id="a"),n(id="b")),links=listOf(WbLink(from="a",to="b",relation=WbRelation.AFTER)));assertEquals(listOf("b","a"),WbRules.analyze(p).order)}
    @Test fun orderCycleDetected(){val p=WbProject(nodes=listOf(n(id="a"),n(id="b")),links=listOf(WbLink(from="a",to="b"),WbLink(from="b",to="a")));assertTrue("CYCLE" in errors(p))}
    @Test fun fallbackCycleDetected(){val p=WbProject(nodes=listOf(n(id="a"),n(id="b")),links=listOf(WbLink(from="a",to="b",relation=WbRelation.FALLBACK),WbLink(from="b",to="a",relation=WbRelation.FALLBACK)));assertTrue("FALLBACK_CYCLE" in errors(p))}
    @Test fun requiredInactiveIsError(){val p=WbProject(nodes=listOf(n(id="a").copy(enabled=false),n(id="b")),links=listOf(WbLink(from="a",to="b")));assertTrue("INACTIVE_REQUIRED" in errors(p))}
    @Test fun optionalInactiveIsNotError(){val p=WbProject(nodes=listOf(n(id="a").copy(enabled=false),n(id="b")),links=listOf(WbLink(from="a",to="b",relation=WbRelation.OPTIONAL)));assertTrue(errors(p).isEmpty())}
    @Test fun activeConflictDetected(){val p=WbProject(nodes=listOf(n(id="a"),n(id="b")),links=listOf(WbLink(from="a",to="b",relation=WbRelation.CONFLICT)));assertTrue("CONFLICT" in errors(p))}
    @Test fun disabledConflictIgnored(){val p=WbProject(nodes=listOf(n(id="a"),n(id="b").copy(enabled=false)),links=listOf(WbLink(from="a",to="b",relation=WbRelation.CONFLICT)));assertTrue(errors(p).isEmpty())}
    @Test fun multipleOsNeedsSlot(){val p=WbProject(nodes=listOf(n("os.pi64","a"),n("os.ubuntu","b")));assertTrue("OS_EXCLUSIVE" in errors(p));assertFalse("OS_EXCLUSIVE" in errors(p.copy(activeOsId="a")))}
    @Test fun selectedOsStillReservesOtherSlots(){val a=n("os.pi64","a");val b=n("os.ubuntu","b");val p=WbProject(nodes=listOf(a,b),activeOsId="a");assertEquals(32768L,WbRules.analyze(p).reservedMiB)}
    @Test fun freeSpaceReallyReserved(){val p=WbProject(cardMiB=512,nodes=listOf(n("storage.free")));assertTrue("STORAGE_OVERFLOW" in errors(p))}
    @Test fun recoveryIsRemovableWithoutReappearance(){val a=n("os.pi64","a");val r=n("storage.recovery","r");val p=WbProject(nodes=listOf(a,r),links=listOf(WbLink(from="r",to="a")));val q=WbEdits.remove(p,setOf("r"));assertEquals(listOf(a),WbCodec.decode(WbCodec.encode(q)).nodes);assertTrue(q.links.isEmpty())}
    @Test fun architectureMismatchDetected(){assertTrue("ARCHITECTURE" in errors(WbProject(architecture="arm32",nodes=listOf(n("os.pi64"))))) }
    @Test fun bedrockCannotRunNativelyOnArm(){val p=WbTemplates.make("Minecraft Bedrock · Official x86_64").copy(architecture="arm64");assertTrue("BEDROCK_ARM" in errors(p));assertTrue(WbExport.files(p).keys.none{it.endsWith("start.sh")})}
    @Test fun nukkitNeedsJava(){assertTrue("JAVA_RUNTIME" in errors(WbProject(nodes=listOf(n("mc.nukkit"))))) }
    @Test fun javaRuntimeMatchChecked(){val java=n("runtime.java").copy(params=mapOf("major" to "8"));assertTrue("JAVA_RUNTIME" in errors(WbProject(nodes=listOf(java,n("mc.java"))))) }
    @Test fun javaHeapCannotBeInverted(){val server=n("mc.java").let{it.copy(params=it.params+("minHeapMiB" to "4096"))};assertTrue("HEAP" in errors(WbProject(nodes=listOf(server)))) }
    @Test fun tcpAndUdpSameNumberAreNotConflict(){val a=n("mc.java").let{it.copy(params=it.params+("port" to "19132"))};val b=n("mc.nukkit");assertFalse("PORT_CONFLICT" in errors(WbProject(nodes=listOf(a,b,n("runtime.java"))))) }
    @Test fun sameUdpPortConflictDetected(){assertTrue("PORT_CONFLICT" in errors(WbProject(nodes=listOf(n("mc.nukkit"),n("mc.nukkit"),n("runtime.java"))))) }
    @Test fun lanDiscoveryPortsAreChecked(){val p=WbTemplates.make("Minecraft Bedrock · Official x86_64");val b=p.nodes.first{it.type=="mc.bedrock"}.let{it.copy(params=it.params+mapOf("port" to "20100","portV6" to "20101","lanVisibility" to "true"))};val q=p.copy(nodes=p.nodes.filterNot{it.type=="mc.bedrock"}+b+n("mc.nukkit")+n("runtime.java"));assertTrue("PORT_CONFLICT" in errors(q))}
    @Test fun conditionsAreConjunctive(){val node=n().copy(conditions=listOf(WbCondition("network","equals","online"),WbCondition("architecture","equals","x86_64")));assertFalse(node.id in WbRules.analyze(WbProject(nodes=listOf(node))).active)}
    @Test fun conditionNegationWorks(){assertTrue(WbRules.matches(WbProject(),WbCondition("network","not equals","offline")))}
    @Test fun unknownConditionsAreNotTrue(){assertFalse(WbRules.matches(WbProject(),WbCondition("random","equals","x")))}
    @Test fun normalScenarioDoesNotStartStandby(){val a=n(id="a").copy(name="Primary");val b=n(id="b").copy(name="Backup");val p=WbProject(nodes=listOf(a,b),links=listOf(WbLink(from="a",to="b",relation=WbRelation.FALLBACK)));assertTrue(WbRules.simulate(p,emptySet()).none{it=="PLANNED START · Backup"})}
    @Test fun failedScenarioSelectsFallback(){val a=n(id="a").copy(name="Primary");val b=n(id="b").copy(name="Backup");val p=WbProject(nodes=listOf(a,b),links=listOf(WbLink(from="a",to="b",relation=WbRelation.FALLBACK)));assertTrue("PLANNED FALLBACK · Backup" in WbRules.simulate(p,setOf("a")))}
    @Test fun invalidPlanHasNoSimulatedSuccess(){val p=WbProject(nodes=listOf(n("minecraft")));assertTrue(WbRules.simulate(p,emptySet()).any{it.startsWith("Blocked by")})}
    @Test fun duplicateRemapsInternalLinks(){val a=n(id="a");val b=n(id="b");val p=WbProject(nodes=listOf(a,b),links=listOf(WbLink(from="a",to="b")));val q=WbEdits.duplicate(p,setOf("a","b"));assertEquals(4,q.nodes.size);assertEquals(2,q.links.size);assertTrue(q.links.last().from !in setOf("a","b"));assertNotEquals(q.links.last().from,q.links.last().to)}
    @Test fun duplicateDoesNotCopyExternalEdges(){val p=WbProject(nodes=listOf(n(id="a"),n(id="b")),links=listOf(WbLink(from="a",to="b")));assertEquals(1,WbEdits.duplicate(p,setOf("a")).links.size)}
    @Test fun layoutRespectsPositionLock(){val p=WbProject(nodes=listOf(n(id="a",x=200f,y=200f).copy(locked=true),n(id="b"),n(id="c")));assertEquals(p.nodes.first(),WbEdits.arrange(p,setOf("a","b","c"),"row").nodes.first())}
    @Test fun alignRightAndBottomKeepLockedBlocksAndLinks(){
        val a=n(id="a",x=40f,y=10f)
        val b=n(id="b",x=400f,y=300f)
        val c=n(id="c",x=900f,y=900f).copy(locked=true)
        val edge=WbLink(from="a",to="b")
        val p=WbProject(nodes=listOf(a,b,c),links=listOf(edge))
        val right=WbEdits.arrange(p,setOf("a","b","c"),"right")
        assertEquals(400f,right.nodes[0].x,0.01f)
        assertEquals(400f,right.nodes[1].x,0.01f)
        assertEquals(c,right.nodes[2])
        assertEquals(p.links,right.links)
        val bottom=WbEdits.arrange(p,setOf("a","b","c"),"bottom")
        assertEquals(300f,bottom.nodes[0].y,0.01f)
        assertEquals(300f,bottom.nodes[1].y,0.01f)
    }
    @Test fun distributionHasNonOverlappingSpacingAndPreservesIdentity(){
        val nodes=listOf(n(id="a",x=0f),n(id="b",x=2f),n(id="c",x=5f))
        val p=WbProject(nodes=nodes)
        val x=WbEdits.arrange(p,nodes.map{it.id}.toSet(),"spread_x")
        val sorted=x.nodes.sortedBy{it.x}
        assertTrue(sorted.zipWithNext().all{(a,b)->b.x-a.x>=WB_NODE_W+39f})
        assertEquals(nodes.map{it.id}.toSet(),x.nodes.map{it.id}.toSet())
        val y=WbEdits.arrange(p,nodes.map{it.id}.toSet(),"spread_y")
        assertTrue(y.nodes.map{it.y}.distinct().size==3)
    }
    @Test fun verticalWireIsExplicitAndIdempotent(){val p=WbProject(nodes=listOf(n(id="a",y=100f),n(id="b",y=0f)));val q=WbEdits.wireVertical(p,setOf("a","b"));assertEquals("b",q.links.single().from);assertEquals(WbRelation.BEFORE,q.links.single().relation);assertEquals(q,WbEdits.wireVertical(q,setOf("a","b")))}
    @Test fun zoomKeepsFocalWorldPoint(){val v=WbView();val before=WbGeometry.world(180f,300f,v);val after=WbGeometry.world(180f,300f,WbGeometry.zoom(v,2f,180f,300f));assertEquals(before.first,after.first,.001f);assertEquals(before.second,after.second,.001f)}
    @Test fun zoomIsBoundedAndFinite(){assertEquals(2.5f,WbGeometry.zoom(WbView(),100f,0f,0f).zoom,0f);assertEquals(WbView(),WbGeometry.zoom(WbView(),Float.NaN,0f,0f))}
    @Test fun hitReturnsTopmost(){val a=n(id="a");val b=n(id="b");assertEquals("b",WbGeometry.hit(listOf(a,b),20f,20f)?.id)}
    @Test fun emptyCanvasHitIsNull(){assertNull(WbGeometry.hit(emptyList(),0f,0f))}
    @Test fun snapRoundsToGrid(){assertEquals(32f,WbGeometry.snap(29f,true),0f);assertEquals(29f,WbGeometry.snap(29f,false),0f)}
    @Test fun fitsPositiveAndNegativeNodes(){val nodes=listOf(n(x=-400f,y=-200f),n(x=300f,y=600f));val v=WbGeometry.fit(nodes,1000f,900f);nodes.forEach{val s=WbGeometry.screen(it.x,it.y,v);assertTrue(s.first>=0&&s.second>=0);assertTrue(s.first+WB_NODE_W*v.zoom<=1001);assertTrue(s.second+WB_NODE_H*v.zoom<=901)}}
    @Test fun undoRedoRestoresImmutableSnapshots(){val h=WbHistory();val p=WbProject();val q=p.copy(name="edited");h.checkpoint(p);assertEquals(p,h.undo(q));assertEquals(q,h.redo(p))}
    @Test fun newEditDropsRedo(){val h=WbHistory();val p=WbProject();h.checkpoint(p);h.undo(p.copy(name="x"));h.checkpoint(p);assertFalse(h.canRedo)}
    @Test fun historyHasBound(){val h=WbHistory(2);val p=WbProject();repeat(3){h.checkpoint(p.copy(name=it.toString()))};assertEquals("2",h.undo(p)?.name);assertEquals("1",h.undo(p)?.name);assertNull(h.undo(p))}
    @Test fun templatesHaveValidIdentifiersAndNoDuplicateFields(){WbTemplates.names.forEach{val p=WbTemplates.make(it);assertEquals(p,WbCodec.decode(WbCodec.encode(p)));assertTrue(errors(p).isEmpty())}}
    @Test fun exportedJavaUsesTcpAndProperEula(){val p=WbTemplates.make("Minecraft Java");val n=p.nodes.first{it.type=="mc.java"};val f=WbExport.files(p);assertTrue(f["servers/${n.id}/eula.txt"]!!.contains("eula=false"));assertTrue(f["servers/${n.id}/start.sh"]!!.contains("eula=true"));assertEquals("25565",properties(WbExport.properties(n)).getProperty("server-port"))}
    @Test fun bedrockAndNukkitPropertiesRemainDistinct(){val b=properties(WbExport.properties(n("mc.bedrock")));val k=properties(WbExport.properties(n("mc.nukkit")));assertEquals("false",b.getProperty("allow-list"));assertNull(b.getProperty("white-list"));assertEquals("false",k.getProperty("white-list"));assertEquals("0",k.getProperty("gamemode"));assertEquals("survival",b.getProperty("gamemode"))}
    @Test fun javaUnicodePropertiesRoundTrip(){val x=n("mc.java").let{it.copy(params=it.params+("motd" to "Hello = ö 🥰"))};assertEquals("Hello = ö 🥰",properties(WbExport.properties(x)).getProperty("motd"))}
    @Test fun multiLinePropertyInjectionRejected(){val x=n("mc.java").let{it.copy(params=it.params+("motd" to "x\nserver-port=1"))};assertTrue(WbCatalog.fieldErrors(x).isNotEmpty());reject{WbExport.properties(x)}}
    @Test fun shellInjectionInWorldRejected(){val x=n("mc.java").let{it.copy(params=it.params+("world" to "a'; echo injected; '"))};assertTrue(WbCatalog.fieldErrors(x).isNotEmpty())}
    @Test fun svgEscapesMarkup(){val p=WbProject(name="<script>alert(1)</script>",nodes=listOf(n().copy(name="<script>x</script>")));val svg=WbExport.svg(p);assertFalse(svg.contains("<script>"));DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray()))}
    @Test fun exportIncludesPortableDocument(){val p=WbProject();val f=WbExport.files(p);assertEquals(p,WbCodec.decode(f.getValue("project.rwb.json")));assertFalse(JSONObject(f.getValue("plan.json")).getBoolean("executed"))}
    @Test fun noCommandsWrittenToRealDisksInLauncher(){val n=n("mc.bedrock");val script=WbExport.launcher(n);assertTrue(script.contains("x86_64"));assertFalse(script.contains("/dev/block"));assertFalse(script.contains("sudo"))}
    @Test fun startScriptUsesFixedBinaryNames(){assertTrue(WbExport.launcher(n("mc.java")).contains("./server.jar"));assertTrue(WbExport.launcher(n("mc.nukkit")).contains("./nukkit.jar"));assertTrue(WbExport.launcher(n("mc.bedrock")).contains("./bedrock_server"))}
    @Test fun externalHttpsSourcesCannotContainCredentials(){assertFalse(WbCodec.safeHttps("https://user:secret@example.com/a"));assertFalse(WbCodec.safeHttps("http://example.com/a"));assertTrue(WbCodec.safeHttps("https://nukkit.org/"))}
    @Test fun inactiveNodeFieldsDoNotBlockActivePlan(){val p=WbProject(nodes=listOf(n("mc.java").copy(enabled=false,params=mapOf("port" to "bad"))));assertTrue(errors(p).isEmpty())}
    @Test fun displayPositionsDoNotChangeSchedulingOrder(){val p=WbProject(nodes=listOf(n(id="a").copy(name="A"),n(id="b").copy(name="B")));assertEquals(WbRules.analyze(p).order,WbRules.analyze(p.copy(nodes=p.nodes.map{it.copy(y=1000-it.y)})).order)}
    @Test fun runtimeBudgetCountsConfiguredHeap(){val p=WbTemplates.make("Minecraft Java").copy(memoryMiB=512);assertTrue("RAM_OVERFLOW" in errors(p))}
    @Test fun completeCatalogAndTemplateCountsAreReported(){println("WORKBENCH_CATALOG blockTypes=${WbCatalog.kinds.size} declaredFields=${WbCatalog.kinds.sumOf{it.fields.size}} editorCommands=${WbTool.entries.size} templates=${WbTemplates.names.size} (not thousands of completed independent functions)")}
    private fun properties(s:String)=Properties().apply{load(ByteArrayInputStream(s.toByteArray()))}
}
