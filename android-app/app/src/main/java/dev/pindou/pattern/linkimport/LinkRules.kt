package dev.pindou.pattern.linkimport

import java.net.InetAddress
import java.net.URI

object LinkRules {
    fun shareUrl(text: String): String {
        require(text.length<=10000)
        val raw=Regex("https://[^\\s<>\\\"'，。；）]+",RegexOption.IGNORE_CASE).find(text)?.value ?: error("分享内容中没有 HTTPS 链接")
        val uri=URI(raw.trimEnd(')','}',',','.',';'))
        require(uri.scheme.equals("https",true) && !uri.host.isNullOrBlank() && uri.userInfo==null && (uri.port==-1 || uri.port==443)) { "仅支持无账号信息的 HTTPS 公网链接" }
        // HTTP clients can bypass custom DNS for numeric IP literals. Validate them here too.
        val host=uri.host
        if(host.contains(':') || host.all { it in '0'..'9' || it=='.' }) {
            require(InetAddress.getAllByName(host).all(::publicAddress)) { "不允许访问本机或内网地址" }
        }
        return uri.toASCIIString()
    }
    fun publicAddress(address: InetAddress): Boolean {
        if(address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b=address.address.map { it.toInt() and 255 }
        if(b.size==4) return b[0] !in listOf(0,10,127) && b[0]<224 &&
            !(b[0]==100 && b[1] in 64..127) && !(b[0]==169 && b[1]==254) &&
            !(b[0]==172 && b[1] in 16..31) && !(b[0]==192 && (b[1]==168 || b[1]==0 || (b[1]==88 && b[2]==99))) && !(b[0]==198 && (b[1] in 18..19 || (b[1]==51 && b[2]==100))) && !(b[0]==203 && b[1]==0 && b[2]==113)
        return b.size==16 && (b[0] and 0xE0)==0x20 && !(b[0]==0x20 && b[1]==2) && !(b[0]==0x20 && b[1]==1 && ((b[2]==0 && b[3]==0) || (b[2]==0x0D && b[3]==0xB8)))
    }
    fun imageUrls(html: String,base: String): List<String> {
        require(html.length<=2_100_000)
        val out=LinkedHashSet<String>()
        fun add(value: String) {
            val v=value.replace("&amp;","&").replace("&#38;","&").replace("&quot;","\"").trim()
            try { out+=shareUrl(URI(base).resolve(v).toString()) } catch(_: Exception) { }
        }
        // Read public hydration data as strings only; never execute page scripts or bypass login.
        fun decodeEscapes(v: String)=v.replace("\\/","/").replace("\\u002F","/",true).replace("\\u0026","&",true).replace("\\u003D","=",true)
        fun structured(text: String) {
            val imageKeys=Regex("\"(?:urlDefault|urlPre|image_url|imageUrl|cover_url)\"\\s*:\\s*\"([^\"\\r\\n]{1,8192})\"")
            for(m in imageKeys.findAll(text)) { if(out.size>=30) break;add(decodeEscapes(m.groupValues[1])) }
            val lists=Regex("\"url_list\"\\s*:\\s*\\[([^\\]]{0,15000})\\]")
            for(list in lists.findAll(text)) for(m in Regex("\"([^\"\\r\\n]{1,8192})\"").findAll(list.groupValues[1])) {
                val v=decodeEscapes(m.groupValues[1])
                if(out.size<30 && Regex("(?i)(\\.(jpg|jpeg|png|webp)(?:[?~!]|$)|douyinpic\\.com|sns-img[^/]*xhscdn\\.com)").containsMatchIn(v)) add(v)
            }
        }
        structured(html)
        for(script in Regex("<script[^>]*id=[\"']RENDER_DATA[\"'][^>]*>(.*?)</script>",setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL)).findAll(html)) {
            try { structured(java.net.URLDecoder.decode(script.groupValues[1],"UTF-8")) } catch(_: Exception) {}
        }
        val attr=Regex("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2",setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL))
        for(tag in Regex("<(?:meta|img)\\b[^>]{0,20000}>",RegexOption.IGNORE_CASE).findAll(html)) {
            val values=attr.findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[3] }
            if(tag.value.startsWith("<meta",true)) {
                val key=values["property"] ?: values["name"] ?: ""
                if(key.lowercase() in listOf("og:image","og:image:secure_url","twitter:image")) values["content"]?.let(::add)
            } else (values["data-src"] ?: values["data-original"] ?: values["src"])?.let(::add)
            if(out.size>=30) break
        }
        return out.take(30)
    }
}
