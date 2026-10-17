package dev.pindou.pattern.image

import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import dev.pindou.pattern.chartimport.ChartRegion
import dev.pindou.pattern.pattern.*
import java.io.File
import kotlin.math.*

object ChartImageDecoder {
    @Suppress("DEPRECATION")
    fun decode(file: File,region: ChartRegion,maxEdge: Int=2048,check: ()->Unit={}): Bitmap {
        require(maxEdge in 64..2048)
        val orientation=ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)
        val decoder=BitmapRegionDecoder.newInstance(file.path,false) ?: error("请选择 JPEG、PNG 或 WebP 图片")
        try {
            val w=decoder.width;val h=decoder.height;require(w>0 && h>0 && w<=100000 && h<=100000)
            val requested=CropRect(region.left.toDouble(),region.top.toDouble(),(region.right-region.left).toDouble(),(region.bottom-region.top).toDouble())
            val raw=ExifGeometry.rect(requested,ExifGeometry.inverse(orientation))
            val rect=Rect(floor(raw.left*w).toInt().coerceIn(0,w-1),floor(raw.top*h).toInt().coerceIn(0,h-1),
                ceil((raw.left+raw.width)*w).toInt().coerceIn(1,w),ceil((raw.top+raw.height)*h).toInt().coerceIn(1,h))
            var sample=1
            while(ceil(max(rect.width(),rect.height()).toDouble()/sample)>maxEdge) sample*=2
            check()
            val bitmap=decoder.decodeRegion(rect,BitmapFactory.Options().apply {
                inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888;inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB)
            }) ?: error("图片区域无法解码")
            var upright: Bitmap?=null
            try {
                check();val image=ImagePipeline.orient(bitmap,orientation);upright=image
                val actual=ExifGeometry.rect(CropRect(rect.left.toDouble()/w,rect.top.toDouble()/h,rect.width().toDouble()/w,rect.height().toDouble()/h),orientation)
                val left=(requested.left-actual.left)/actual.width*image.width
                val top=(requested.top-actual.top)/actual.height*image.height
                val width=requested.width/actual.width*image.width;val height=requested.height/actual.height*image.height
                val output=Bitmap.createBitmap(ceil(width).toInt().coerceIn(1,maxEdge),ceil(height).toInt().coerceIn(1,maxEdge),Bitmap.Config.ARGB_8888)
                try {
                    Canvas(output).apply { drawColor(Color.WHITE);scale((output.width/width).toFloat(),(output.height/height).toFloat());translate(-left.toFloat(),-top.toFloat());drawBitmap(image,0f,0f,Paint(Paint.FILTER_BITMAP_FLAG)) }
                    check();return output
                } catch(t: Throwable) { output.recycle();throw t }
            } finally { upright?.takeIf { it!==bitmap }?.recycle();bitmap.recycle() }
        } finally { decoder.recycle() }
    }
}
