package dev.mietze.piimager

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest

class ConnectAssetSourceTest {
    private val url="https://connect-assets.raspberrypi.com/assets/application-c183cd62.js"
    private val target="https://connect.raspberrypi.com/assets/application-c183cd62.js"
    private fun source(code:Int=200,mime:String="text/javascript",cors:String=ConnectAssetPolicy.ORIGIN,
                       body:String="window.example=true;", inspect:(Request)->Unit={}):ConnectAssetSource {
        val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
            inspect(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                .header("Content-Type",mime).header("Access-Control-Allow-Origin",cors)
                .body(body.toResponseBody()).build()
        }.build()
        return ConnectAssetSource(client)
    }
    private fun rejected(block:()->Unit){try{block();fail("Expected rejected response")}catch(_:IOException){}}
    @Test fun mapsOnlyExactPublicScript(){assertEquals(target,ConnectAssetPolicy.target(url))}
    @Test fun rejectsHttp(){assertNull(ConnectAssetPolicy.target(url.replace("https:","http:")))}
    @Test fun rejectsExternalHost(){assertNull(ConnectAssetPolicy.target(url.replace("connect-assets.raspberrypi.com","evil.test")))}
    @Test fun rejectsSuffixSpoof(){assertNull(ConnectAssetPolicy.target(url.replace(".com/",".com.evil.test/")))}
    @Test fun rejectsUserInfo(){assertNull(ConnectAssetPolicy.target(url.replace("https://","https://user:pass@")))}
    @Test fun rejectsAuthQuery(){assertNull(ConnectAssetPolicy.target("$url?token=secret"))}
    @Test fun rejectsEmptyQuery(){assertNull(ConnectAssetPolicy.target("$url?"))}
    @Test fun rejectsFragment(){assertNull(ConnectAssetPolicy.target("$url#x"))}
    @Test fun rejectsWhitespace(){assertNull(ConnectAssetPolicy.target(" $url"))}
    @Test fun rejectsCustomPort(){assertNull(ConnectAssetPolicy.target(url.replace(".com/",".com:8443/")))}
    @Test fun permitsHttpsDefaultPort(){assertEquals(target,ConnectAssetPolicy.target(url.replace(".com/",".com:443/")))}
    @Test fun permitsCaseInsensitiveHost(){assertEquals(target,ConnectAssetPolicy.target(url.replace("connect-assets.raspberrypi.com","CONNECT-ASSETS.RASPBERRYPI.COM")))}
    @Test fun rejectsMainDocuments(){assertNull(ConnectAssetPolicy.target(url,mainFrame=true))}
    @Test fun rejectsPost(){assertNull(ConnectAssetPolicy.target(url,method="POST"))}
    @Test fun rejectsNonFingerprintedScripts(){assertNull(ConnectAssetPolicy.target(url.replace("-c183cd62","")))}
    @Test fun rejectsApiPath(){assertNull(ConnectAssetPolicy.target(url.replace("/assets/","/api/")))}
    @Test fun rejectsEncodedTraversal(){assertNull(ConnectAssetPolicy.target(url.replace("/assets/","/assets/%2e%2e/")))}
    @Test fun rejectsDotTraversal(){assertNull(ConnectAssetPolicy.target(url.replace("/assets/","/assets/../")))}
    @Test fun rejectsAuthHost(){assertNull(ConnectAssetPolicy.target(url.replace("connect-assets.","id.")))}
    @Test fun rejectsHtmlAndImages(){assertNull(ConnectAssetPolicy.target(url.replace(".js",".html")));assertNull(ConnectAssetPolicy.target(url.replace(".js",".png")))}
    @Test fun rejectsMalformedUri(){assertNull(ConnectAssetPolicy.target("https://[broken/assets/a-12345678.js"))}
    @Test fun bytesAreUnmodified(){assertEquals("window.example=true;",source().load(url)!!.bytes.toString(Charsets.UTF_8))}
    @Test fun requestContainsNoPrivateHeaders(){
        source(inspect={request->
            assertEquals(target,request.url.toString());assertEquals("GET",request.method)
            assertNull(request.header("Cookie"));assertNull(request.header("Authorization"));assertNull(request.header("Referer"))
            assertEquals(ConnectAssetPolicy.ORIGIN,request.header("Origin"))
        }).load(url)
    }
    @Test fun rejectsForbidden(){rejected{source(code=403).load(url)}}
    @Test fun rejectsRedirect(){rejected{source(code=302).load(url)}}
    @Test fun rejectsEmptyHttpSuccess(){rejected{source(body="").load(url)}}
    @Test fun rejectsHtmlMime(){rejected{source(mime="text/html").load(url)}}
    @Test fun rejectsHtmlDisguisedAsScript(){rejected{source(body=" \n<html>Log in</html>").load(url)}}
    @Test fun rejectsBomHtml(){rejected{source(body="\uFEFF<!doctype html>").load(url)}}
    @Test fun rejectsMissingCors(){rejected{source(cors="").load(url)}}
    @Test fun rejectsWrongCors(){rejected{source(cors="https://evil.test").load(url)}}
    @Test fun acceptsPublicWildcardCors(){assertNotNull(source(cors="*").load(url))}
    @Test fun acceptsCharsetMime(){assertEquals("text/javascript",source(mime="text/javascript; charset=utf-8").load(url)!!.mime)}
    @Test fun rejectsOversizedBody(){rejected{source(body="x".repeat(ConnectAssetPolicy.MAX_BYTES+1)).load(url)}}
    @Test fun doesNotFetchUnrecognizedUrl(){var hit=false;assertNull(source(inspect={hit=true}).load("https://evil.test/a.js"));assertFalse(hit)}

    @Test fun mapsVersionedStylesheet(){assertEquals(target.replace(".js",".css"),ConnectAssetPolicy.target(url.replace(".js",".css")))}
    @Test fun mapsNestedPublicStyle(){assertEquals("https://connect.raspberrypi.com/assets/raspberry_pi/header_and_footer-2252205b.css",ConnectAssetPolicy.target("https://connect-assets.raspberrypi.com/assets/raspberry_pi/header_and_footer-2252205b.css"))}
    @Test fun cssMustNotContainTraversal(){assertNull(ConnectAssetPolicy.target("https://connect-assets.raspberrypi.com/assets/../header_and_footer-2252205b.css"))}
    @Test fun javascriptCannotReturnCss(){rejected{source(mime="text/css").load(url)}}
    @Test fun cssCannotReturnJavascript(){rejected{source().load(url.replace(".js",".css"))}}
    @Test fun cssReturnsUnchangedBytes(){val css=".xterm{position:relative}";assertEquals(css,source(mime="text/css",body=css).load(url.replace(".js",".css"))!!.bytes.toString(Charsets.UTF_8))}
    @Test fun cssCannotReturnLoginHtml(){rejected{source(mime="text/css",body="<html>login</html>").load(url.replace(".js",".css"))}}
    @Test fun cssMimeAcceptsCharset(){assertTrue(ConnectAssetPolicy.validMime("text/css; charset=utf-8",true))}

    /** Explicit opt-in public endpoint integration probe; never uses app cookies. */
    @Test fun currentOfficialScriptLiveProbe(){
        assumeTrue("Set RM_CONNECT_LIVE_ASSET_TEST=1 for public endpoint test",System.getenv("RM_CONNECT_LIVE_ASSET_TEST")=="1")
        val client=OkHttpClient.Builder().build()
        val html=client.newCall(Request.Builder().url("https://connect.raspberrypi.com/sign-in").build()).execute().use {
            assertEquals(200,it.code);it.body!!.string()
        }
        val asset=Regex("https://connect-assets\\.raspberrypi\\.com/assets/[A-Za-z0-9_.-]+-[a-fA-F0-9]{8,64}\\.js").find(html)!!.value
        var primary:ByteArray?=null
        var primaryMime=""
        var primaryCors=""
        val originalStatus=client.newCall(Request.Builder().url(asset).build()).execute().use{
            primaryMime=it.header("Content-Type").orEmpty()
            primaryCors=it.header("Access-Control-Allow-Origin").orEmpty()
            if(it.code==200 && ConnectAssetPolicy.validMime(primaryMime)) primary=it.body!!.bytes()
            it.code
        }
        val loaded=ConnectAssetSource().load(asset)!!
        assertTrue(loaded.bytes.size>10_000)
        primary?.let{assertArrayEquals("Official origin must serve identical provider bytes",it,loaded.bytes)}
        assertTrue(loaded.bytes.toString(Charsets.UTF_8).contains("popup-button"))
        val sha=MessageDigest.getInstance("SHA-256").digest(loaded.bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
        val styles=Regex("https://connect-assets\\.raspberrypi\\.com/assets/(?:[A-Za-z0-9_-]+/){0,2}[A-Za-z0-9_.-]+-[a-fA-F0-9]{8,64}\\.css").findAll(html).map{it.value}.distinct().take(4).toList()
        assertTrue(styles.isNotEmpty())
        for(style in styles) {
            val css=ConnectAssetSource().load(style)!!
            assertEquals("text/css",css.mime);assertTrue(css.bytes.isNotEmpty())
            println("LIVE_PUBLIC_STYLESHEET canonicalStatus=200 bytes=${css.bytes.size} file=${style.substringAfterLast('/')}")
        }
        println("LIVE_PUBLIC_ASSET originalStatus=$originalStatus canonicalStatus=200 bytes=${loaded.bytes.size} sha256=$sha unchangedProviderController=true primaryIdentical=${primary!=null} primaryMime=$primaryMime primaryCors=$primaryCors")
    }
}
