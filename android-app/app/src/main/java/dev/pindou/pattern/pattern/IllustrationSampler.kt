package dev.pindou.pattern.pattern

import kotlin.math.*

enum class SamplingStyle { ILLUSTRATION, PHOTO }
data class ConversionOptions(val style: SamplingStyle = SamplingStyle.ILLUSTRATION) {
    val description: String get() = if(style==SamplingStyle.PHOTO) "照片·面积平均" else "插画·保留细线"
}
data class LinearSample(val r: Double, val g: Double, val b: Double) {
    val luminance: Double get() = .2126*r+.7152*g+.0722*b
    val spread: Double get() = maxOf(r,g,b)-minOf(r,g,b)
    fun lab(): Lab = ColorMath.linearToLab(r,g,b)
}

/** Per-job reusable bins. Dominant real colors and supported dark strokes replace mixed gray edges.
 * Photo mode continues to use AreaSampler unchanged. No global threshold or image-wide blackening.
 */
class IllustrationSampler {
    private val linear=DoubleArray(256) { ColorMath.linear(it/255.0) }
    private val background=Mard.backgroundRgb
    private val bg=doubleArrayOf(linear[background shr 16 and 255],linear[background shr 8 and 255],linear[background and 255])
    private val weights=DoubleArray(4096)
    private val red=DoubleArray(4096); private val green=DoubleArray(4096); private val blue=DoubleArray(4096)
    private val touched=IntArray(4096)
    private var touchedCount=0
    private fun encoded(v: Double): Int = ((if(v<=.0031308) 12.92*v else 1.055*v.pow(1/2.4)-.055)*255).roundToInt().coerceIn(0,255)

    fun sample(pixels: IntArray, width: Int, height: Int, rect: CropRect): LinearSample {
        require(pixels.size==width*height && rect.width>0 && rect.height>0)
        for(i in 0 until touchedCount) {
            val bin=touched[i]; weights[bin]=0.0; red[bin]=0.0; green[bin]=0.0; blue[bin]=0.0
        }
        touchedCount=0
        var total=0.0; var rTotal=0.0; var gTotal=0.0; var bTotal=0.0
        for(y in floor(rect.top).toInt().coerceAtLeast(0) until ceil(rect.top+rect.height).toInt().coerceAtMost(height)) {
            val wy=min(y+1.0,rect.top+rect.height)-max(y.toDouble(),rect.top)
            for(x in floor(rect.left).toInt().coerceAtLeast(0) until ceil(rect.left+rect.width).toInt().coerceAtMost(width)) {
                val weight=wy*(min(x+1.0,rect.left+rect.width)-max(x.toDouble(),rect.left))
                if(weight<=0) continue
                val pixel=pixels[y*width+x]; val alpha=(pixel ushr 24)/255.0
                val ri=pixel shr 16 and 255; val gi=pixel shr 8 and 255; val bi=pixel and 255
                val r=linear[ri]*alpha+bg[0]*(1-alpha)
                val g=linear[gi]*alpha+bg[1]*(1-alpha)
                val b=linear[bi]*alpha+bg[2]*(1-alpha)
                val rgb=when(alpha) { 1.0 -> pixel; 0.0 -> background; else -> (encoded(r) shl 16) or (encoded(g) shl 8) or encoded(b) }
                val bin=((rgb shr 20 and 15) shl 8) or ((rgb shr 12 and 15) shl 4) or (rgb shr 4 and 15)
                if(weights[bin]==0.0) touched[touchedCount++]=bin
                weights[bin]+=weight; red[bin]+=r*weight; green[bin]+=g*weight; blue[bin]+=b*weight
                total+=weight; rTotal+=r*weight; gTotal+=g*weight; bTotal+=b*weight
            }
        }
        val missing=(rect.width*rect.height-total).coerceAtLeast(0.0)
        if(missing>1e-9) {
            val bin=((background shr 20 and 15) shl 8) or ((background shr 12 and 15) shl 4) or (background shr 4 and 15)
            if(weights[bin]==0.0) touched[touchedCount++]=bin
            weights[bin]+=missing; red[bin]+=bg[0]*missing; green[bin]+=bg[1]*missing; blue[bin]+=bg[2]*missing
            total+=missing; rTotal+=bg[0]*missing; gTotal+=bg[1]*missing; bTotal+=bg[2]*missing
        }
        require(total>0)
        var winner=touched[0]; var darkest=1.0
        for(i in 0 until touchedCount) {
            val bin=touched[i]
            if(weights[bin]>weights[winner] || (weights[bin]==weights[winner] && bin<winner)) winner=bin
            val c=LinearSample(red[bin]/weights[bin],green[bin]/weights[bin],blue[bin]/weights[bin])
            if(c.spread<=.14) darkest=min(darkest,c.luminance)
        }
        var darkWeight=0.0; var dr=0.0; var dg=0.0; var db=0.0
        if(darkest<.32) for(i in 0 until touchedCount) {
            val bin=touched[i]
            val c=LinearSample(red[bin]/weights[bin],green[bin]/weights[bin],blue[bin]/weights[bin])
            if(c.spread<=.14 && c.luminance<=darkest+.05) {
                darkWeight+=weights[bin]; dr+=red[bin]; dg+=green[bin]; db+=blue[bin]
            }
        }
        // A narrow light stroke between dark sides must not be swallowed by dark preference.
        // Return its sampled color, never a forced palette white or black.
        if(darkWeight/total in .15.. .95) {
            LightStrokeSampler.sample(pixels,width,height,rect,linear,bg)?.let { return it }
        }
        val fraction=darkWeight/total
        if(fraction in .16.. .75) {
            val dark=LinearSample(dr/darkWeight,dg/darkWeight,db/darkWeight)
            val rest=LinearSample((rTotal-dr)/(total-darkWeight),(gTotal-dg)/(total-darkWeight),(bTotal-db)/(total-darkWeight))
            if(rest.luminance-dark.luminance>=.14) return dark
        }
        if(weights[winner]/total>=.30) return LinearSample(red[winner]/weights[winner],green[winner]/weights[winner],blue[winner]/weights[winner])
        return LinearSample(rTotal/total,gTotal/total,bTotal/total)
    }
}
