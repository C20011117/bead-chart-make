package dev.pindou.pattern.project

import dev.pindou.pattern.chartimport.*
import dev.pindou.pattern.pattern.*
import java.io.*
import java.util.zip.CRC32

data class ChartProject(val name: String,val sourceHash: String,val grid: GridSize,val region: ChartRegion,
    val legendRegion: ChartRegion,val legend: List<LegendEntry>,val draft: RecognitionDraft?,val mardConfirmed: Boolean=false) {
    init { require(name.length in 1..100);require(sourceHash.matches(Regex("[0-9a-f]{64}")));require(legend.size<=500);require(draft==null || draft.grid==grid) }
}
object ProjectCodec {
    fun encode(p: ChartProject): ByteArray {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { d ->
            d.writeInt(0x42454144);d.writeInt(1);d.writeUTF(Mard.palette.id);d.writeUTF(p.name);d.writeUTF(p.sourceHash)
            d.writeInt(p.grid.columns);d.writeInt(p.grid.rows)
            for(r in listOf(p.region,p.legendRegion)) { d.writeFloat(r.left);d.writeFloat(r.top);d.writeFloat(r.right);d.writeFloat(r.bottom) }
            d.writeInt(p.legend.size)
            p.legend.forEach { d.writeInt(it.rgb);d.writeUTF(it.code);d.writeBoolean(it.confirmed) }
            d.writeBoolean(p.mardConfirmed)
            d.writeBoolean(p.draft!=null)
            p.draft?.let { draft ->
                draft.snapshot().forEach(d::writeInt)
                draft.evidence.forEach { d.writeInt(it.suggested);d.writeInt(it.rgb);d.writeFloat(it.score);d.writeUTF(it.reason) }
            }
        }
        val payload=bytes.toByteArray();val crc=CRC32().apply { update(payload) }.value
        return ByteArrayOutputStream().also { it.write(payload);DataOutputStream(it).writeLong(crc) }.toByteArray()
    }
    fun decode(bytes: ByteArray): ChartProject {
        require(bytes.size in 16..2_000_000)
        val payload=bytes.copyOf(bytes.size-8);val expected=DataInputStream(ByteArrayInputStream(bytes,bytes.size-8,8)).readLong()
        require(CRC32().apply { update(payload) }.value==expected) { "工程校验失败" }
        DataInputStream(ByteArrayInputStream(payload)).use { d ->
            require(d.readInt()==0x42454144 && d.readInt()==1) { "不支持的工程格式" }
            require(d.readUTF()==Mard.palette.id) { "色库版本不匹配" }
            val name=d.readUTF();val hash=d.readUTF();val grid=GridSize(d.readInt(),d.readInt())
            fun readRegion()=ChartRegion(d.readFloat(),d.readFloat(),d.readFloat(),d.readFloat())
            val region=readRegion();val legendRegion=readRegion();val count=d.readInt();require(count in 0..500)
            val legend=List(count) { LegendEntry(d.readInt(),d.readUTF(),d.readBoolean()) }
            val mard=d.readBoolean()
            val draft=if(d.readBoolean()) {
                val cells=IntArray(grid.columns*grid.rows) { d.readInt() }
                RecognitionDraft(grid,cells,List(cells.size) { CellEvidence(d.readInt(),d.readInt(),d.readFloat(),d.readUTF()) })
            } else null
            require(d.available()==0)
            return ChartProject(name,hash,grid,region,legendRegion,legend,draft,mard)
        }
    }
}
