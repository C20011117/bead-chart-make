package dev.pindou.pattern.pattern

import kotlin.math.*

/** Exact pixel-overlap weights; compositing and averaging happen in linear sRGB. */
object AreaSampler {
    private val linear = DoubleArray(256) { ColorMath.linear(it / 255.0) }
    private val background = doubleArrayOf(linear[Mard.backgroundRgb shr 16 and 255],
        linear[Mard.backgroundRgb shr 8 and 255],linear[Mard.backgroundRgb and 255])
    fun sample(pixels: IntArray, width: Int, height: Int, rect: CropRect): Lab {
        require(pixels.size == width*height && rect.width > 0 && rect.height > 0)
        var r = 0.0; var g = 0.0; var b = 0.0; var area = 0.0
        for (y in floor(rect.top).toInt().coerceAtLeast(0) until ceil(rect.top+rect.height).toInt().coerceAtMost(height)) {
            val wy = min(y+1.0,rect.top+rect.height)-max(y.toDouble(),rect.top)
            for (x in floor(rect.left).toInt().coerceAtLeast(0) until ceil(rect.left+rect.width).toInt().coerceAtMost(width)) {
                val weight = wy*(min(x+1.0,rect.left+rect.width)-max(x.toDouble(),rect.left))
                val pixel = pixels[y*width+x]; val alpha = (pixel ushr 24)/255.0
                r += (linear[pixel shr 16 and 255]*alpha+background[0]*(1-alpha))*weight
                g += (linear[pixel shr 8 and 255]*alpha+background[1]*(1-alpha))*weight
                b += (linear[pixel and 255]*alpha+background[2]*(1-alpha))*weight
                area += weight
            }
        }
        if(rect.left<0 || rect.top<0 || rect.left+rect.width>width || rect.top+rect.height>height) {
            val missing=(rect.width*rect.height-area).coerceAtLeast(0.0)
            r+=background[0]*missing; g+=background[1]*missing; b+=background[2]*missing; area+=missing
        }
        require(area>0)
        return ColorMath.linearToLab(r/area,g/area,b/area)
    }
}
