package dev.mietze.piimager.workbench
import org.junit.Assert.*
import org.junit.Test
class WorkbenchMinecraftTreeTest {
    @Test fun minecraftLivesMidCatalogueOrder(){assertTrue(wbCategoryRank("Minecraft")>wbCategoryRank("Runtimes"));assertTrue(wbCategoryRank("Minecraft")<wbCategoryRank("Services"))}
    @Test fun treeChildrenExist(){listOf("mc.nukkit","mc.bedrock","mc.bukkit","mc.java","mc.mod.forge","mc.mod.fabric").forEach{assertTrue(WbCatalog.contains(it))}}
    @Test fun javaFamilyHasBukkit(){assertEquals("Bukkit",WbCatalog.kind("mc.bukkit").title)}
    @Test fun modClientsArePlans(){assertEquals("Client plan",WbCatalog.kind("mc.mod.forge").exportMode);assertEquals("Client plan",WbCatalog.kind("mc.mod.fabric").exportMode)}
    @Test fun bukkitUsesJavaPort(){assertEquals("25565",WbCatalog.create("mc.bukkit").value("port"))}
    @Test fun bukkitRequiresJava(){val p=WbProject(nodes=listOf(WbCatalog.create("mc.bukkit")));assertTrue(WbRules.analyze(p).findings.any{it.code=="JAVA_RUNTIME"})}
    @Test fun modClientRequiresJava(){val p=WbProject(nodes=listOf(WbCatalog.create("mc.mod.fabric")));assertTrue(WbRules.analyze(p).findings.any{it.code=="JAVA_RUNTIME"})}
    @Test fun modClientDoesNotEmitServerLauncher(){val p=WbProject(nodes=listOf(WbCatalog.create("os.pi64"),WbCatalog.create("runtime.java"),WbCatalog.create("mc.mod.forge")));val f=WbExport.files(p);assertTrue(f.keys.any{it.contains("client-plan.properties")});assertFalse(f.keys.any{it.endsWith("start.sh")})}
    @Test fun bukkitPropertiesExport(){val n=WbCatalog.create("mc.bukkit");assertTrue(WbExport.properties(n).contains("server-port=25565"))}
    @Test fun everyKindHasKnownCategoryRankOrNotes(){WbCatalog.kinds.forEach{assertTrue(it.category=="Notes"||wbCategoryRank(it.category)<999)}}
}
