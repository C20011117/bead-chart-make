package dev.pindou.pattern

import dev.pindou.pattern.linkimport.LinkRules
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class LinkRulesTest {
    @Test fun extractsUrlFromShareText() { assertEquals("https://example.com/chart",LinkRules.shareUrl("分享图纸 https://example.com/chart 。复制查看")) }
    @Test(expected=IllegalArgumentException::class) fun credentialsRejected() { LinkRules.shareUrl("https://name:secret@example.com/a") }
    @Test(expected=IllegalArgumentException::class) fun unusualPortRejected() { LinkRules.shareUrl("https://example.com:8443/a") }
    @Test(expected=IllegalStateException::class) fun httpNotSilentlyUpgraded() { LinkRules.shareUrl("http://example.com/a") }
    @Test fun localAndReservedAddressesRejected() {
        for(ip in listOf("127.0.0.1","0.0.0.0","10.0.0.1","192.168.0.1","172.16.0.1","169.254.169.254","100.64.0.1","198.18.0.1","224.0.0.1","::1","fc00::1","fe80::1","192.0.2.1","198.51.100.1","203.0.113.1","64:ff9b::a00:1","2001:db8::1","2002:a00:1::1")) assertFalse(ip,LinkRules.publicAddress(InetAddress.getByName(ip)))
        assertTrue(LinkRules.publicAddress(InetAddress.getByName("8.8.8.8")))
    }
    @Test fun parsesPublicMetadataAndLazyImagesWithoutScripts() {
        val html="""<meta content="/a.png?x=1&amp;y=2" property="og:image"><img data-src='https://cdn.example.com/b.jpg'><img src='/a.png?x=1&amp;y=2'><img src='data:image/png;base64,x'>"""
        assertEquals(listOf("https://example.com/a.png?x=1&y=2","https://cdn.example.com/b.jpg"),LinkRules.imageUrls(html,"https://example.com/page"))
    }
    @Test fun parsesPublicImageHydrationWithoutExecutingCode() {
        val html="""<script>window.state={"imageList":[{"urlDefault":"https:\/\/sns-img.example.com\/chart.webp?a=1\u0026b=2"}],"images":[{"url_list":["https://p3.douyinpic.com/chart.jpeg"]}]};</script>"""
        assertEquals(listOf("https://sns-img.example.com/chart.webp?a=1&b=2","https://p3.douyinpic.com/chart.jpeg"),LinkRules.imageUrls(html,"https://example.com/"))
    }
    @Test fun parsesPercentEncodedPublicRenderData() {
        val json="""{"images":[{"url_list":["https://cdn.example.com/chart.png"]}]}"""
        val html="<script id='RENDER_DATA' type='application/json'>"+java.net.URLEncoder.encode(json,"UTF-8")+"</script>"
        assertEquals(listOf("https://cdn.example.com/chart.png"),LinkRules.imageUrls(html,"https://example.com/"))
    }
    @Test fun oversizedMalformedHydrationDoesNotOverflowRegexStack() {
        val html="<script>{\"urlDefault\":\""+"a".repeat(100000)+"</script>"
        assertTrue(LinkRules.imageUrls(html,"https://example.com/").isEmpty())
    }
    @Test fun numericIpUrlsCannotBypassDnsGuard() {
        for(host in listOf("127.0.0.1","10.0.0.1","169.254.169.254","2130706433","[::1]","[64:ff9b::a00:1]")) {
            try { LinkRules.shareUrl("https://$host/");fail(host) } catch(_: IllegalArgumentException) {}
        }
        assertEquals("https://8.8.8.8/",LinkRules.shareUrl("https://8.8.8.8/"))
        assertEquals("https://[2606:4700:4700::1111]",LinkRules.shareUrl("https://[2606:4700:4700::1111]"))
    }
    @Test fun noCandidatesMeansNoClaimedExtraction() { assertTrue(LinkRules.imageUrls("<script>loadPrivateImages()</script>","https://example.com/").isEmpty()) }
}
