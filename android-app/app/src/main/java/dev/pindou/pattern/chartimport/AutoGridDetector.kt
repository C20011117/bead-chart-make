package dev.pindou.pattern.chartimport

import dev.pindou.pattern.pattern.GridSize
import kotlin.math.*

data class AutoGridResult(val primary: GridSuggestion,val withCoordinateBands: GridSuggestion?,val note: String)

/** On-device geometric vision, not an OCR/cloud model. All results require human approval. */
object AutoGridDetector {
    private data class Axis(val origin: Double,val pitch: Double,val first: Int,val last: Int,val quality: Double) {
        fun at(k: Int)=origin+k*pitch
    }
    fun detect(pixels: IntArray,width: Int,height: Int,check: ()->Unit={}): AutoGridResult? {
        require(width>0 && height>0 && width<=2048 && height<=2048 && pixels.size==width*height)
        if(width<48 || height<48) return null
        val gray=FloatArray(pixels.size) { val c=pixels[it];(.2126*(c shr 16 and 255)+.7152*(c shr 8 and 255)+.0722*(c and 255)).toFloat() }
        val vx=DoubleArray(width);val vy=DoubleArray(height);val sx=DoubleArray(width);val sy=DoubleArray(height)
        for(y in 2 until height-2) {
            if(y%32==0) check()
            for(x in 2 until width-2) {
                val i=y*width+x;val ex=max(0f,min(gray[i-2],gray[i+2])-gray[i]);val ey=max(0f,min(gray[i-2*width],gray[i+2*width])-gray[i])
                vx[x]+=ex/height;vy[y]+=ey/width;if(ex>10) sx[x]+=1.0/height;if(ey>10) sy[y]+=1.0/width
            }
        }
        val ax=axis(vx,sx,check) ?: return null;val ay=axis(vy,sy,check) ?: return null
        var left=ax.first;var right=ax.last;var top=ay.first;var bottom=ay.last
        fun sample(x: Double,y: Double)=gray[y.roundToInt().coerceIn(0,height-1)*width+x.roundToInt().coerceIn(0,width-1)].toDouble()
        // Paired bright, glyph-bearing outside bands are coordinate candidates, not certainly axes.
        fun glyphBand(horizontal: Boolean,band: Int,from: Int,to: Int): Boolean {
            if(to-from<8) return false
            var matches=0
            for(k in from until to) {
                check()
                val x=ax.at(if(horizontal) k else band);val y=ay.at(if(horizontal) band else k)
                val corners=doubleArrayOf(sample(x+.15*ax.pitch,y+.15*ay.pitch),sample(x+.85*ax.pitch,y+.15*ay.pitch),sample(x+.15*ax.pitch,y+.85*ay.pitch),sample(x+.85*ax.pitch,y+.85*ay.pitch)).sorted()
                val bg=(corners[1]+corners[2])/2
                var ink=0;var count=0
                for(yy in (y+.25*ay.pitch).roundToInt() until (y+.8*ay.pitch).roundToInt())
                    for(xx in (x+.2*ax.pitch).roundToInt() until (x+.85*ax.pitch).roundToInt()) { if(sample(xx.toDouble(),yy.toDouble())<bg-40) ink++;count++ }
                val fraction=ink.toDouble()/max(1,count)
                if(bg in 185.0..250.0 && corners.last()-corners.first()<50 && fraction in .035.. .65) matches++
            }
            return matches.toDouble()/(to-from)>.70
        }
        val trimX=glyphBand(false,left,top,bottom)&&glyphBand(false,right-1,top,bottom)
        val trimY=glyphBand(true,top,left,right)&&glyphBand(true,bottom-1,left,right)
        if(trimX) {left++;right--};if(trimY) {top++;bottom--}
        val quality=min(ax.quality,ay.quality).toFloat().coerceIn(0f,1f)
        fun suggestion(l: Int,t: Int,r: Int,b: Int): GridSuggestion? {
            if(r-l !in 8..128 || b-t !in 8..128) return null
            val region=runCatching { ChartRegion((ax.at(l)/width).toFloat().coerceIn(0f,1f),(ay.at(t)/height).toFloat().coerceIn(0f,1f),
                (ax.at(r)/width).toFloat().coerceIn(0f,1f),(ay.at(b)/height).toFloat().coerceIn(0f,1f)) }.getOrNull() ?: return null
            return GridSuggestion(GridSize(r-l,b-t),quality,region)
        }
        val result=suggestion(left,top,right,bottom) ?: return null
        val alternative=if(trimX||trimY) suggestion(ax.first,ay.first,ax.last,ay.last) else null
        check()
        return AutoGridResult(result,alternative,if(trimX||trimY) "已排除疑似外围坐标带；请检查边缘白格是否保留，可切换未裁坐标候选。" else "已根据重复格线拟合起点与格距；请检查主体范围、四角与行列数。")
    }
    private fun axis(v: DoubleArray,support: DoubleArray,check: ()->Unit): Axis? {
        val mean=v.average();val z=DoubleArray(v.size) { v[it]-mean };val corr=DoubleArray(v.size/8+1)
        if(z.sumOf { it*it }<1e-5) return null
        for(d in 3 until corr.size) {
            check();var a=0.0;var b=0.0;var c=0.0
            for(i in d until z.size) { a+=z[i]*z[i-d];b+=z[i]*z[i];c+=z[i-d]*z[i-d] }
            corr[d]=a/sqrt(max(1e-12,b*c))
        }
        var period=0;var best=.35;val candidates=ArrayList<Pair<Int,Double>>()
        for(d in 3 until corr.size-1) if(corr[d]>=.25&&corr[d]>=corr[d-1]&&corr[d]>=corr[d+1]) {
            // Reward a fundamental whose 2..5 multiples also repeat, instead of selecting bold 5/10-cell lines.
            var score=corr[d]
            for(k in 2..5) if(d*k<corr.size) {
                val radius=(k+1)/2;val peak=((d*k-radius).coerceAtLeast(3)..(d*k+radius).coerceAtMost(corr.lastIndex)).maxOf { corr[it] }
                score+=max(0.0,peak)*.2
            }
            candidates+=d to score
            if(score>best) {best=score;period=d}
        }
        if(period==0) return null
        period=candidates.filter { it.second>=best*.97 }.minOf { it.first }
        // After rescaling, fine lines can be weak at integer lags while a bold 5-cell period is sharp.
        if(period>=35) {
            val coarse=period
            val divisors=(2..10).map { (coarse.toDouble()/it).roundToInt() }.distinct().filter { d ->
                d>=3 && d*4<corr.size && corr[d]>=.12 && (2..4).all { m ->
                    val radius=(m+1)/2
                    ((d*m-radius).coerceAtLeast(3)..(d*m+radius).coerceAtMost(corr.lastIndex)).maxOf { corr[it] }>=.12
                }
            }
            if(divisors.isNotEmpty()) period=divisors.min()
        }
        fun near(t: Double,radius: Int): Int {
            val lo=(t.roundToInt()-radius).coerceAtLeast(2);val hi=(t.roundToInt()+radius).coerceAtMost(v.size-3)
            if(lo>hi) return t.roundToInt().coerceIn(2,v.size-3)
            return (lo..hi).maxBy { v[it] }
        }
        var harmonicSum=0.0;var harmonicWeight=0.0
        for(multiple in 1..5) if(period*multiple<corr.size) {
            val radius=if(multiple==1) 0 else (multiple+1)/2
            val peak=((period*multiple-radius).coerceAtLeast(3)..(period*multiple+radius).coerceAtMost(corr.lastIndex)).maxBy { corr[it] }
            val weight=max(0.0,corr[peak]);harmonicSum+=weight*multiple*peak;harmonicWeight+=weight*multiple*multiple
        }
        val refined=harmonicSum/harmonicWeight
        var phase=0.0;var strength=-1.0
        for(step in 0 until ceil(refined*2).toInt()) {
            val o=step*.5
            var sum=0.0;var count=0
            for(j in 0..(v.size/refined).toInt()) { val i=o+j*refined;if(i>=2&&i<v.size-2) {val p=near(i,2);sum+=v[p]*v[p];count++} }
            val score=sum/max(1,count);if(score>strength) {strength=score;phase=o}
        }
        val maxV=v.max();var sw=0.0;var sk=0.0;var sp=0.0;var skk=0.0;var skp=0.0
        for(k in 0..(v.size/refined).toInt()) {
            val i=phase+k*refined
            if(i>=2&&i<v.size-2) {val p=near(i,2)
                if(v[p]>maxV*.17&&support[p]>.15) {val w=v[p]*v[p];sw+=w;sk+=w*k;sp+=w*p;skk+=w*k*k;skp+=w*k*p}
            }
        }
        val denominator=sw*skk-sk*sk;if(sw<=0||abs(denominator)<1e-8) return null
        val pitch=(sw*skp-sk*sp)/denominator;val origin=(sp-pitch*sk)/sw
        if(!pitch.isFinite()||abs(pitch-period)>period*.12) return null
        val good=ArrayList<Int>();val threshold=support.max()*.27
        for(j in -1..(v.size/pitch).toInt()+1) {
            val t=origin+pitch*j;if(t<2||t>=v.size-2) continue
            val p=near(t,1);if(support[p]>=threshold&&v[p]>=2) good+=j
        }
        if(good.size<9) return null
        var run=ArrayList<Int>();var longest=emptyList<Int>()
        for(j in good) { if(run.isNotEmpty()&&j-run.last()>6) {if(run.size>longest.size) longest=run;run=ArrayList()};run+=j }
        if(run.size>longest.size) longest=run
        if(longest.size<9) return null
        var first=longest.first();var last=longest.last()
        if(abs(origin+pitch*(first-1))<=2) first--
        if(abs(origin+pitch*(last+1)-v.size)<=2) last++
        if(last-first<8 || last-first>132) return null
        return Axis(origin,pitch,first,last,corr[period])
    }
}
