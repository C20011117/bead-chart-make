package dev.pindou.pattern.chartimport

import dev.pindou.pattern.pattern.*
import kotlin.math.*

data class GridSuggestion(val grid: GridSize,val quality: Float,val relativeRegion: ChartRegion=ChartRegion())
object GridDetector {
    /** Autocorrelation of axis edge projections; returns a suggestion, never overwrites calibration. */
    fun suggest(pixels: IntArray,w: Int,h: Int): GridSuggestion? {
        if(w<48 || h<48) return null
        fun luma(c: Int)=.2126*(c shr 16 and 255)+.7152*(c shr 8 and 255)+.0722*(c and 255)
        val px=DoubleArray(w);val py=DoubleArray(h)
        for(y in 1 until h) for(x in 1 until w) {
            val c=luma(pixels[y*w+x]);px[x]+=abs(c-luma(pixels[y*w+x-1]))/h;py[y]+=abs(c-luma(pixels[(y-1)*w+x]))/w
        }
        fun period(p: DoubleArray): Pair<Int,Double>? {
            val mean=p.average();val v=DoubleArray(p.size) { p[it]-mean }
            if(v.sumOf { it*it }<1e-6) return null
            var best=0;var score=.35
            for(d in max(3,ceil(p.size/128.0).toInt())..p.size/8) {
                var a=0.0;var b=0.0;var c=0.0
                for(i in d until p.size) { a+=v[i]*v[i-d];b+=v[i]*v[i];c+=v[i-d]*v[i-d] }
                val candidate=a/sqrt(max(1e-12,b*c))-.0005*d
                if(candidate>score) { best=d;score=candidate }
            }
            return if(best==0) null else best to score
        }
        val x=period(px) ?: return null;val y=period(py) ?: return null
        fun phase(p: DoubleArray,period: Int): Int = (0 until period).maxBy { offset ->
            var sum=0.0;var count=0
            for(i in offset until p.size step period) { sum+=p[i];count++ }
            sum/max(1,count)
        }
        val ox=phase(px,x.first);val oy=phase(py,y.first)
        val grid=GridSize(((w-ox).toFloat()/x.first).roundToInt().coerceIn(8,128),((h-oy).toFloat()/y.first).roundToInt().coerceIn(8,128))
        val region=ChartRegion(ox.toFloat()/w,oy.toFloat()/h,((ox+grid.columns*x.first).toFloat()/w).coerceAtMost(1f),((oy+grid.rows*y.first).toFloat()/h).coerceAtMost(1f))
        return GridSuggestion(grid,min(x.second,y.second).toFloat().coerceIn(0f,1f),region)
    }
}
