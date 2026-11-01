package dev.mietze.piimager
import org.junit.Assert.*
import org.junit.Test
class ConnectClientRecoveryTest {
 private fun act(enabled:Boolean=false, reason:String="This action requires JavaScript.")=ConnectNativeAction("a_1","shell","Remote shell",enabled,reason)
 @Test fun disabledBootstrapIsClientError() { assertTrue(ConnectTranslationPolicy.clientNotInitialized(act())) }
 @Test fun missingControlIsNotBootstrapFailure() { assertFalse(ConnectTranslationPolicy.clientNotInitialized(null as ConnectNativeAction?)) }
 @Test fun enabledControlIsNotFailure() { assertFalse(ConnectTranslationPolicy.clientNotInitialized(act(true))) }
 @Test fun disabledServiceIsNotBootstrapFailure() { assertFalse(ConnectTranslationPolicy.clientNotInitialized(act(reason="Shell access is disabled"))) }
 @Test fun handlesCase() { assertTrue(ConnectTranslationPolicy.clientNotInitialized(act(reason="This action REQUIRES JAVASCRIPT."))) }
 @Test fun nativeReasonExplainsClient() { assertEquals(ConnectTranslationPolicy.CLIENT_START_ERROR,ConnectTranslationPolicy.unavailable(true,"unknown",act())) }
 @Test fun noInternetTakesPriority() { assertTrue(ConnectTranslationPolicy.unavailable(false,"unknown",act())!!.startsWith("You're not connected.")) }
 @Test fun staleReasonIsNotShownWhenEnabled() { assertNull(ConnectTranslationPolicy.unavailable(true,"online",act(true))) }
 @Test fun stateFindsDeviceFailure() { assertTrue(ConnectTranslationPolicy.clientNotInitialized(ConnectNativeState(page="devices",devices=listOf(ConnectNativeDevice("a_2","Pi","unknown",null,act(),null))))) }
 @Test fun normalStateRemainsNormal() { assertFalse(ConnectTranslationPolicy.clientNotInitialized(ConnectNativeState(page="devices"))) }
}
