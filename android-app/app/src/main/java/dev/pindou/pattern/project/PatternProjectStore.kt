package dev.pindou.pattern.project

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class PatternProjectStore(private val context: Context) {
    private val root=File(context.filesDir,"chart-projects").apply { mkdirs() }
    private fun readBounded(input: java.io.InputStream): ByteArray {
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384)
        while(true) { val n=input.read(buffer);if(n<0) break;require(out.size()+n<=2_000_000) { "工程过大" };out.write(buffer,0,n) }
        return out.toByteArray()
    }
    fun folder(id: String): File { require(id.matches(Regex("[a-f0-9-]{36}")));return File(root,id) }
    fun source(id: String)=File(folder(id),"source.img")
    fun hash(id: String): String=source(id).inputStream().use { stream ->
        val digest=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536)
        while(true) { val n=stream.read(b);if(n<0) break;digest.update(b,0,n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun import(uri: Uri,check: ()->Unit): String {
        val id=UUID.randomUUID().toString();folder(id).mkdirs()
        try {
            val input=context.contentResolver.openInputStream(uri) ?: error("无法读取图片")
            input.use { from -> source(id).outputStream().use { to ->
                var size=0L;val b=ByteArray(65536)
                while(true) { check();val n=from.read(b);if(n<0) break;size+=n;require(size<=32L*1024*1024) { "图片大于32MB，请先裁剪" };to.write(b,0,n) }
                require(size>0)
            } }
            return id
        } catch(t: Throwable) { folder(id).deleteRecursively();throw t }
    }
    fun save(id: String,project: ChartProject) {
        require(source(id).isFile)
        val bytes=ProjectCodec.encode(project);ProjectCodec.decode(bytes)
        val file=AtomicFile(File(folder(id),"project.bin"));val out=file.startWrite()
        try { out.write(bytes);file.finishWrite(out) } catch(t: Throwable) { file.failWrite(out);throw t }
    }
    fun load(id: String): ChartProject {
        val file=AtomicFile(File(folder(id),"project.bin"))
        val bytes=file.openRead().use { readBounded(it) }
        val p=ProjectCodec.decode(bytes);require(p.sourceHash==hash(id)) { "原图副本校验失败" };return p
    }
    fun list(): List<Pair<String,String>> = root.listFiles().orEmpty().filter { it.isDirectory && (File(it,"project.bin").isFile || File(it,"project.bin.bak").isFile) }
        .sortedByDescending { File(it,"project.bin").lastModified() }.take(100).mapNotNull { f ->
            try { val bytes=AtomicFile(File(f,"project.bin")).openRead().use { readBounded(it) };f.name to ProjectCodec.decode(bytes).name } catch(_: Exception) { f.name to "工程损坏或版本不兼容（点击查看）" }
        }
}
