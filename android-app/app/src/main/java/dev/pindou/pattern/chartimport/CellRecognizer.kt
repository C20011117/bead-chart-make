package dev.pindou.pattern.chartimport

import dev.pindou.pattern.pattern.*
import kotlin.math.*

data class BackgroundSample(val rgb: Int,val purity: Float)
object CellRecognizer {
    private class BackgroundSampler {
        private val weights=DoubleArray(4096);private val r=DoubleArray(4096);private val g=DoubleArray(4096);private val b=DoubleArray(4096)
        private val touched=IntArray(4096);private var touchedCount=0
    /** Multiple inset bands/corners: labels at the center and border grid lines are downweighted. */
    fun background(pixels: IntArray,w: Int,h: Int,rect: CropRect): BackgroundSample {
        require(pixels.size==w*h && rect.width>0 && rect.height>0)
        for(j in 0 until touchedCount) { val i=touched[j];weights[i]=0.0;r[i]=0.0;g[i]=0.0;b[i]=0.0 }
        touchedCount=0
        var total=0.0
        for(y in floor(rect.top+rect.height*.16).toInt().coerceAtLeast(0) until ceil(rect.top+rect.height*.84).toInt().coerceAtMost(h)) {
            for(x in floor(rect.left+rect.width*.16).toInt().coerceAtLeast(0) until ceil(rect.left+rect.width*.84).toInt().coerceAtMost(w)) {
                val u=(x+.5-rect.left)/rect.width;val v=(y+.5-rect.top)/rect.height
                val weight=if(u in .32.. .68 && v in .32.. .68) .2 else 1.0
                val c=pixels[y*w+x];val a=(c ushr 24)/255.0
                val rr=(c shr 16 and 255)*a+255*(1-a);val gg=(c shr 8 and 255)*a+255*(1-a);val bb=(c and 255)*a+255*(1-a)
                val bin=((rr.toInt() shr 4) shl 8) or ((gg.toInt() shr 4) shl 4) or (bb.toInt() shr 4)
                if(weights[bin]==0.0) touched[touchedCount++]=bin
                weights[bin]+=weight;r[bin]+=rr*weight;g[bin]+=gg*weight;b[bin]+=bb*weight;total+=weight
            }
        }
        if(total==0.0) return BackgroundSample(0xFFFFFF,0f)
        var i=touched[0]
        for(j in 1 until touchedCount) { val index=touched[j];if(weights[index]>weights[i] || (weights[index]==weights[i] && index<i)) i=index }
        val rgb=(r[i].div(weights[i]).roundToInt() shl 16) or (g[i].div(weights[i]).roundToInt() shl 8) or b[i].div(weights[i]).roundToInt()
        return BackgroundSample(rgb,(weights[i]/total).toFloat())
    }
    }
    fun background(pixels: IntArray,w: Int,h: Int,rect: CropRect)=BackgroundSampler().background(pixels,w,h,rect)
    fun recognize(pixels: IntArray,w: Int,h: Int,grid: GridSize,legend: List<LegendEntry>,check: ()->Unit={},progress: (Float)->Unit={}): RecognitionDraft {
        require(w>0 && h>0 && pixels.size==w*h)
        val candidates=legend.filter { it.confirmed && it.index()!=null }.map { Triple(it.index()!!,ColorMath.rgbToLab(it.rgb),it.rgb) }
        require(candidates.isNotEmpty()) { "请先核对至少一个图例色号" }
        val sampler=BackgroundSampler()
        val cells=IntArray(grid.columns*grid.rows);val evidence=ArrayList<CellEvidence>(cells.size)
        for(y in 0 until grid.rows) {
            check()
            for(x in 0 until grid.columns) {
                if(x%8==0) check()
                val s=sampler.background(pixels,w,h,CropRect(x*w.toDouble()/grid.columns,y*h.toDouble()/grid.rows,w.toDouble()/grid.columns,h.toDouble()/grid.rows))
                val lab=ColorMath.rgbToLab(s.rgb)
                val distances=candidates.map { it.first to ColorMath.deltaE00(lab,it.second) }.sortedBy { it.second }
                val best=distances.first();val second=distances.firstOrNull { it.first!=best.first }?.second ?: 100.0
                val small=min(w.toFloat()/grid.columns,h.toFloat()/grid.rows)<6f
                val white=ColorMath.deltaE00(lab,ColorMath.rgbToLab(0xFFFFFF))<8.0
                val reason=when {
                    white -> "白豆或空格需人工选择"
                    small -> "每格不足6像素"
                    best.second>9 -> "与已确认图例色差较大"
                    second-best.second<2.5 -> "图例存在近似或相同颜色"
                    s.purity<.55 -> "文字、网格或杂色干扰"
                    else -> ""
                }
                val score=(s.purity*(1-best.second/30).coerceIn(0.0,1.0)*((second-best.second)/8).coerceIn(0.0,1.0)).toFloat()
                evidence+=CellEvidence(best.first,s.rgb,score,reason)
                cells[y*grid.columns+x]=if(reason.isEmpty()) best.first else PENDING_CELL
            }
            progress((y+1f)/grid.rows)
        }
        check();return RecognitionDraft(grid,cells,evidence)
    }
}
