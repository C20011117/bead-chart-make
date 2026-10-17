package dev.pindou.pattern.linkimport

import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/** No account/cookies/cloud parser. DNS answers and every redirect are checked before connecting. */
class PublicImageFetcher {
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES).dns(object: Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = Dns.SYSTEM.lookup(hostname).also {
                require(it.isNotEmpty() && it.all(LinkRules::publicAddress)) { "不允许访问本机或内网地址" }
            }
        }).connectTimeout(12,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()
    private fun open(raw: String,check: ()->Unit): Response {
        var url=LinkRules.shareUrl(raw)
        repeat(6) {
            check()
            val response=client.newCall(Request.Builder().url(url).header("User-Agent","BeadPattern/0.5 PublicImageImport").build()).execute()
            if(response.code in 300..399) {
                val location=response.header("Location");response.close()
                require(location!=null) { "跳转地址缺失" }
                url=LinkRules.shareUrl(java.net.URI(url).resolve(location).toString())
            } else { if(!response.isSuccessful) { val code=response.code;response.close();error("网页返回 $code；请在原平台保存图片后导入") };return response }
        }
        error("网页跳转次数过多")
    }
    fun discover(text: String,check: ()->Unit): List<String> = open(text,check).use { response ->
        val type=response.body?.contentType()?.toString().orEmpty()
        if(type.startsWith("image/")) return@use listOf(response.request.url.toString())
        require(type.contains("html",true)) { "此链接不是公开图片网页" }
        val bytes=read(response,2_000_000,check)
        val charset=response.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        LinkRules.imageUrls(bytes.toString(charset),response.request.url.toString()).also {
            require(it.isNotEmpty()) { "公开网页未提供可提取图片，可能需要登录或动态加载；请保存原图后本地导入" }
        }
    }
    fun download(url: String,file: File,check: ()->Unit) {
        try {
            open(url,check).use { response ->
                val type=response.body?.contentType()?.toString().orEmpty().substringBefore(';')
                require(type in listOf("image/jpeg","image/png","image/webp")) { "仅支持 JPEG、PNG、WebP 原图" }
                val body=response.body ?: error("图片为空");require(body.contentLength()<=32L*1024*1024)
                body.byteStream().use { input -> file.outputStream().use { out ->
                    val b=ByteArray(65536);var size=0L
                    while(true) { check();val n=input.read(b);if(n<0) break;size+=n;require(size<=32L*1024*1024) { "图片超过32MB" };out.write(b,0,n) }
                    require(size>0)
                } }
            }
            check()
        } catch(t: Throwable) { file.delete();throw t }
    }
    private fun read(response: Response,max: Int,check: ()->Unit): ByteArray {
        val body=response.body ?: error("网页为空");require(body.contentLength()<=max)
        return body.byteStream().use { input ->
            val out=ByteArrayOutputStream();val b=ByteArray(16384)
            while(true) { check();val n=input.read(b);if(n<0) break;require(out.size()+n<=max) { "网页过大" };out.write(b,0,n) }
            out.toByteArray()
        }
    }
}
