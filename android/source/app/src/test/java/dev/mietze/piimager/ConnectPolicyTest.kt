package dev.mietze.piimager

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ConnectOriginsTest(private val address: String?, private val expected: Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: internal={1}")
        fun values(): List<Array<Any?>> = listOf(
            arrayOf("https://connect.raspberrypi.com/", true),
            arrayOf("https://id.raspberrypi.com/sign-in", true),
            arrayOf("https://connect.raspberrypi.com:443/devices", true),
            arrayOf("https://CONNECT.RASPBERRYPI.COM/", true),
            arrayOf("https://connect.raspberrypi.com/auth/raspberry_pi/callback?state=example&code=example", true),
            arrayOf("https://connect.raspberrypi.com/devices/example#session", true),
            arrayOf("https://id.raspberrypi.com/session", true),
            arrayOf("http://connect.raspberrypi.com/", false),
            arrayOf("https://connect.raspberrypi.com.evil.test/", false),
            arrayOf("https://evil-connect.raspberrypi.com/", false),
            arrayOf("https://raspberrypi.com/", false),
            arrayOf("https://www.raspberrypi.com/", false),
            arrayOf("https://connect-assets.raspberrypi.com/", false),
            arrayOf("https://challenges.cloudflare.com/", false),
            arrayOf("https://connect.raspberrypi.com@evil.test/", false),
            arrayOf("https://evil.test@connect.raspberrypi.com/", false),
            arrayOf("https://user:secret@connect.raspberrypi.com/", false),
            arrayOf("https://connect.raspberrypi.com:8443/", false),
            arrayOf("https://connect.raspberrypi.com:80/", false),
            arrayOf("https://connect.raspberrypi.com./", false),
            arrayOf("https://connect%2eraspberrypi.com/", false),
            arrayOf("https://connect.raspberrypi.com\\@evil.test/", false),
            arrayOf(" https://connect.raspberrypi.com/", false),
            arrayOf("https://connect.raspberrypi.com/\n", false),
            arrayOf("https://connect.raspberrypi.com/\u0000", false),
            arrayOf("https://127.0.0.1/", false),
            arrayOf("https://[::1]/", false),
            arrayOf("https://evil.test/?next=https://connect.raspberrypi.com/", false),
            arrayOf("javascript:alert(1)", false),
            arrayOf("file:///data/data/example", false),
            arrayOf("content://example/item", false),
            arrayOf("intent://connect.raspberrypi.com/", false),
            arrayOf("data:text/html,test", false),
            arrayOf("about:blank", false),
            arrayOf("//connect.raspberrypi.com/", false),
            arrayOf(null, false),
            arrayOf("", false)
        )
    }
    @Test fun validateExactOrigin() { assertEquals(expected, ConnectPolicy.isInternal(address)) }
}

class ConnectPolicyTest {
    @Test fun connectOriginCanUseClipboard() { assertTrue(ConnectPolicy.isConnect("https://connect.raspberrypi.com/devices")) }
    @Test fun identityOriginCannotUseClipboard() { assertFalse(ConnectPolicy.isConnect("https://id.raspberrypi.com/")) }
    @Test fun unknownOriginCannotUseClipboard() { assertFalse(ConnectPolicy.isConnect("https://evil.test/")) }
    @Test fun nullOriginCannotUseClipboard() { assertFalse(ConnectPolicy.isConnect(null)) }
    @Test fun authOriginRecognized() { assertTrue(ConnectPolicy.isAuth("https://id.raspberrypi.com/sign-in")) }
    @Test fun captchaFrameAllowed() { assertTrue(ConnectPolicy.isFrameAllowed("https://challenges.cloudflare.com/turnstile")) }
    @Test fun initialFramesAllowed() { assertTrue(ConnectPolicy.isFrameAllowed("about:blank")); assertTrue(ConnectPolicy.isFrameAllowed("about:srcdoc")) }
    @Test fun insecureFrameBlocked() { assertFalse(ConnectPolicy.isFrameAllowed("http://example.test/")) }
    @Test fun fileFrameBlocked() { assertFalse(ConnectPolicy.isFrameAllowed("file:///etc/passwd")) }
    @Test fun userPopupAllowed() { assertTrue(ConnectPolicy.canOpenPopup(ConnectPolicy.HOME, true, 1)) }
    @Test fun unpromptedPopupBlocked() { assertFalse(ConnectPolicy.canOpenPopup(ConnectPolicy.HOME, false, 1)) }
    @Test fun sixthWindowIsLimit() { assertTrue(ConnectPolicy.canOpenPopup(ConnectPolicy.HOME, true, 5)); assertFalse(ConnectPolicy.canOpenPopup(ConnectPolicy.HOME, true, 6)) }
    @Test fun externalPopupBlocked() { assertFalse(ConnectPolicy.canOpenPopup("https://evil.test", true, 1)) }
    @Test fun zeroWindowsCannotOpenPopup() { assertFalse(ConnectPolicy.canOpenPopup(ConnectPolicy.HOME, true, 0)) }
    @Test fun loginHostHidesQuerySecrets() { assertEquals("id.raspberrypi.com", ConnectPolicy.hostLabel("https://id.raspberrypi.com/auth?code=SECRET")) }
    @Test fun callbackClassificationDoesNotExposeCode() { assertEquals("Sign-in", ConnectPolicy.routeLabel("https://connect.raspberrypi.com/auth/raspberry_pi/callback?code=SECRET")) }
    @Test fun shellClassification() { assertEquals("Remote shell", ConnectPolicy.routeLabel("https://connect.raspberrypi.com/sessions/example/shell")) }
    @Test fun screenClassification() { assertEquals("Screen sharing", ConnectPolicy.routeLabel("https://connect.raspberrypi.com/sessions/example/vnc")) }
    @Test fun rejectOversizedUrl() { assertNull(ConnectPolicy.httpsUri(ConnectPolicy.HOME + "a".repeat(17000))) }
    @Test fun clipboardLimitIsBounded() { assertEquals(65_536, ConnectPolicy.MAX_CLIPBOARD) }
}
