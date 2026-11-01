package dev.mietze.piimager
import org.junit.Assert.*
import org.junit.Test
class MagicRecoveryTest {
 private val os=MagicBlock("os","Raspberry Pi OS","OS",8)
 private val recovery=MagicBlock("recovery","Recovery image","RECOVERY",8)
 @Test fun emptyLayoutAddsNoRecovery(){assertTrue(decodeMagicBlocks("[]").isEmpty())}
 @Test fun osReloadDoesNotAddRecovery(){assertEquals(listOf(os),decodeMagicBlocks(encodeMagicBlocks(listOf(os))))}
 @Test fun explicitlyAddedRecoverySurvivesReload(){assertEquals(listOf(os,recovery),decodeMagicBlocks(encodeMagicBlocks(listOf(os,recovery))))}
 @Test fun recoveryIsAlwaysRemovable(){assertEquals(listOf(os),removeMagicBlock(listOf(os,recovery),recovery.id))}
 @Test fun deletingRecoveryClearsDependentLink(){assertEquals(listOf(os),removeMagicBlock(listOf(os.copy(dependency=recovery.id),recovery),recovery.id))}
 @Test fun deletingAnotherBlockKeepsRecovery(){assertEquals(listOf(recovery),removeMagicBlock(listOf(os,recovery),os.id))}
 @Test fun deletedRecoveryDoesNotReappear(){assertEquals(listOf(os),decodeMagicBlocks(encodeMagicBlocks(removeMagicBlock(listOf(os,recovery),recovery.id))))}
 @Test fun unknownDeletionDoesNotChangeLayout(){assertEquals(listOf(os,recovery),removeMagicBlock(listOf(os,recovery),"absent"))}
}
