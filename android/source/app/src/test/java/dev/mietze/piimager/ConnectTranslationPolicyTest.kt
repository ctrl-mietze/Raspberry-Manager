package dev.mietze.piimager

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConnectTranslationPolicyTest {
    private val epoch = "123e4567-e89b-12d3-a456-426614174000"
    private fun root() = JSONObject().put("version", 1).put("epoch", epoch).put("page", "devices")
        .put("devices", JSONArray()).put("actions", JSONArray()).put("viewport", "")
    private fun action(handle: String = "a_1", kind: String = "shell", enabled: Boolean = true) = JSONObject()
        .put("handle", handle).put("kind", kind).put("enabled", enabled).put("label", "Remote shell")
    private fun device(key: String = "a_1") = JSONObject().put("key", key).put("name", "mietzberrypi")
        .put("status", "online").put("shell", action("a_2")).put("screen", action("a_3", "screen", false))
    private fun denied(change: (JSONObject) -> Unit) {
        val value = root().apply(change)
        try { ConnectTranslationPolicy.parse(value.toString()); fail("Expected invalid metadata to fail closed") }
        catch (_: IllegalArgumentException) { }
        catch (_: org.json.JSONException) { }
    }
    @Test fun validDeviceMetadata() {
        val s=ConnectTranslationPolicy.parse(root().put("devices", JSONArray().put(device())).toString())
        assertEquals("mietzberrypi",s.devices.single().name);assertTrue(s.devices.single().shell!!.enabled)
        assertFalse(s.devices.single().screen!!.enabled);assertEquals("online",s.devices.single().status)
    }
    @Test fun emptyAccount() { assertTrue(ConnectTranslationPolicy.parse(root().toString()).devices.isEmpty()) }
    @Test fun unknownStatusNotOnline() {
        val d=device().put("status","connected-probably")
        assertEquals("unknown",ConnectTranslationPolicy.parse(root().put("devices",JSONArray().put(d)).toString()).devices.single().status)
    }
    @Test fun unknownVersionRejected() = denied { it.put("version",2) }
    @Test fun missingVersionRejected() = denied { it.remove("version") }
    @Test fun malformedEpochRejected() = denied { it.put("epoch","javascript:alert(1)") }
    @Test fun unknownPageRejected() = denied { it.put("page","execute") }
    @Test fun invalidDeviceHandleRejected() = denied { it.put("devices",JSONArray().put(device("https://evil.test"))) }
    @Test fun duplicateDevicesRejected() = denied { it.put("devices",JSONArray().put(device()).put(device())) }
    @Test fun deviceLimitEnforced() = denied { val a=JSONArray();repeat(101){i->a.put(device("a_$i"))};it.put("devices",a) }
    @Test fun actionLimitEnforced() = denied { val a=JSONArray();repeat(11){i->a.put(action("a_$i"))};it.put("actions",a) }
    @Test fun unknownCommandRejected() = denied { it.put("actions",JSONArray().put(action(kind="delete-account"))) }
    @Test fun javascriptActionRejected() = denied { it.put("actions",JSONArray().put(action("a_1');window.evil()"))) }
    @Test fun iframeActionRejected() = denied { it.put("actions",JSONArray().put(action("iframe:a_1"))) }
    @Test fun unknownViewportRejected() = denied { it.put("page","session").put("viewport","website") }
    @Test fun viewportNotAllowedOnDevicePage() = denied { it.put("viewport","screen") }
    @Test fun viewportAllowedOnSession() {
        val s=ConnectTranslationPolicy.parse(root().put("page","session").put("viewport","terminal").toString())
        assertTrue(s.renderReady)
    }
    @Test fun sessionWithoutViewportIsNotReady() {
        assertFalse(ConnectTranslationPolicy.parse(root().put("page","session").toString()).renderReady)
    }
    @Test fun defaultIsLoadingNotConnected() { val s=ConnectNativeState();assertEquals("loading",s.page);assertFalse(s.renderReady) }
    @Test fun missingServicesAreAbsent() {
        val d=device().remove("shell")
        val value=device();value.remove("shell")
        assertNull(ConnectTranslationPolicy.parse(root().put("devices",JSONArray().put(value)).toString()).devices.single().shell)
    }
    @Test fun absentEnabledDefaultsFalse() {
        val a=action();a.remove("enabled")
        assertFalse(ConnectTranslationPolicy.parse(root().put("actions",JSONArray().put(a)).toString()).actions.single().enabled)
    }
    @Test fun stringsBounded() {
        val d=device().put("name","x".repeat(1000))
        assertEquals(120,ConnectTranslationPolicy.parse(root().put("devices",JSONArray().put(d)).toString()).devices.single().name.length)
    }
    @Test fun controlsNormalized() { assertEquals("a b c",ConnectTranslationPolicy.clean("a\n\t b\r c")) }
    @Test fun hugeDocumentRejected() {
        try { ConnectTranslationPolicy.parse("x".repeat(131_073));fail() } catch (_:IllegalArgumentException) { }
    }
    @Test fun metadataCountsNeverNegative() {
        val s=ConnectTranslationPolicy.parse(root().put("structure",JSONObject().put("forms",-2)).toString())
        assertEquals(0,JSONObject(s.structure).getInt("forms"))
    }
    private val live=ConnectNativeAction("a_1","shell","Remote shell",true)
    @Test fun noInternetHasSpecificError() { assertTrue(ConnectTranslationPolicy.unavailable(false,"online",live)!!.contains("You're not connected")) }
    @Test fun offlinePiHasSpecificError() { assertTrue(ConnectTranslationPolicy.unavailable(true,"offline",live)!!.contains("Pi is reported offline")) }
    @Test fun unknownStatusDoesNotBlockAvailableAction() { assertNull(ConnectTranslationPolicy.unavailable(true,"unknown",live)) }
    @Test fun differentNetworkDoesNotBlock() { assertNull(ConnectTranslationPolicy.unavailable(true,"online",live)) }
    @Test fun unavailableServiceNotBypassed() { assertNotNull(ConnectTranslationPolicy.unavailable(true,"online",live.copy(enabled=false))) }
    @Test fun providerReasonPreserved() {
        assertEquals("Shell disabled",ConnectTranslationPolicy.unavailable(true,"online",live.copy(enabled=false,reason="Shell disabled")))
    }
    @Test fun absentActionIsNotInvented() { assertTrue(ConnectTranslationPolicy.unavailable(true,"online",null)!!.contains("not provided")) }
    @Test fun allowOnlyOpaqueHandles() {
        assertTrue(ConnectTranslationPolicy.handleAllowed("a_42"))
        listOf("a_", "a_123456789", "../../a_1", "a_1\n", "a_1?x=1", "1", "eval").forEach { assertFalse(ConnectTranslationPolicy.handleAllowed(it)) }
    }
}
