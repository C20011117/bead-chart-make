package dev.pindou.pattern.image

import android.content.ContentResolver
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dev.pindou.pattern.pattern.*
import kotlinx.coroutines.*
import kotlin.math.*

object ImagePipeline {
    data class WorkingImage(val bitmap: Bitmap, val crop: CropRect)

    // ImageDecoder applies all EXIF orientations, mirrors, and the requested sRGB transform.
    suspend fun decode(resolver: ContentResolver, uri: Uri): Bitmap {
        var owned: Bitmap? = null
        try {
            return withContext(Dispatchers.IO) {
                decodePreview(resolver,uri).also { owned=it; ensureActive() }
            }
        } catch(e: Throwable) { owned?.recycle(); throw e }
    }

    private fun decodePreview(resolver: ContentResolver, uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver,uri)) { decoder, info, _ ->
            require(info.size.width > 0 && info.size.height > 0) { "图片尺寸无效" }
            val ratio = min(1.0,1024.0/max(info.size.width,info.size.height))
            decoder.setTargetSize(max(1,(info.size.width*ratio).toInt()),max(1,(info.size.height*ratio).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            decoder.setOnPartialImageListener { false }
        }

    /** Locate upright crop first, invert EXIF, then decode only the raw crop at <=2048px. */
    @Suppress("DEPRECATION")
    suspend fun decodeRegion(resolver: ContentResolver, uri: Uri, grid: GridSize, crop: CropSpec): WorkingImage = withContext(Dispatchers.IO) {
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
        } ?: error("图片不可读取")
        resolver.openFileDescriptor(uri,"r")?.use { descriptor ->
            val decoder = BitmapRegionDecoder.newInstance(descriptor.fileDescriptor,false)
                ?: error("不支持此图片格式，请选择 JPEG、PNG 或 WebP 图片")
            try {
                val rawWidth = decoder.width; val rawHeight = decoder.height
                require(rawWidth>0 && rawHeight>0)
                val width = if(ExifGeometry.swapsAxes(orientation)) rawHeight else rawWidth
                val height = if(ExifGeometry.swapsAxes(orientation)) rawWidth else rawHeight
                val requested = if(crop.fitImage) CropRect(0.0,0.0,width.toDouble(),height.toDouble()) else cropRect(width,height,grid,crop)
                val normalized = CropRect(requested.left/width,requested.top/height,requested.width/width,requested.height/height)
                val raw = ExifGeometry.rect(normalized,ExifGeometry.inverse(orientation))
                val region = Rect(floor(raw.left*rawWidth).toInt().coerceIn(0,rawWidth-1),floor(raw.top*rawHeight).toInt().coerceIn(0,rawHeight-1),
                    ceil((raw.left+raw.width)*rawWidth).toInt().coerceIn(1,rawWidth),ceil((raw.top+raw.height)*rawHeight).toInt().coerceIn(1,rawHeight))
                var sample = 1
                while(ceil(max(region.width(),region.height()).toDouble()/sample)>2048) sample *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888
                    inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB)
                }
                ensureActive()
                val decoded = decoder.decodeRegion(region,options) ?: error("无法解码裁剪区域")
                var upright: Bitmap? = null
                try {
                    ensureActive()
                    upright = orient(decoded,orientation)
                    ensureActive()
                    val actual = ExifGeometry.rect(CropRect(region.left.toDouble()/rawWidth,region.top.toDouble()/rawHeight,
                        region.width().toDouble()/rawWidth,region.height().toDouble()/rawHeight),orientation)
                    val bounds = CropRect(
                        ((normalized.left-actual.left)/actual.width*upright.width).coerceAtLeast(0.0),
                        ((normalized.top-actual.top)/actual.height*upright.height).coerceAtLeast(0.0),
                        normalized.width/actual.width*upright.width,
                        normalized.height/actual.height*upright.height)
                    WorkingImage(upright,if(crop.fitImage) cropRect(upright.width,upright.height,grid,crop) else bounds)
                } catch(e: Throwable) { upright?.takeIf { it!==decoded }?.recycle(); decoded.recycle(); throw e }
                finally { if(upright!=null && upright!==decoded) decoded.recycle() }
            } finally { decoder.recycle() }
        } ?: error("图片不可读取")
    }

    internal fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        if(orientation !in 2..8) return bitmap
        val w=bitmap.width; val h=bitmap.height
        val ow=if(ExifGeometry.swapsAxes(orientation)) h else w
        val oh=if(ExifGeometry.swapsAxes(orientation)) w else h
        val corners=listOf(0.0 to 0.0,1.0 to 0.0,0.0 to 1.0)
        val dest=corners.flatMap { (x,y) -> ExifGeometry.point(x,y,orientation).let { listOf((it.first*ow).toFloat(),(it.second*oh).toFloat()) } }.toFloatArray()
        val matrix=Matrix().apply { setPolyToPoly(floatArrayOf(0f,0f,w.toFloat(),0f,0f,h.toFloat()),0,dest,0,3) }
        val result=Bitmap.createBitmap(ow,oh,Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap,matrix,Paint())
        return result
    }

    suspend fun generate(resolver: ContentResolver, uri: Uri, grid: GridSize, crop: CropSpec, options: ConversionOptions = ConversionOptions(), progress: (Float)->Unit): Pattern {
        // Keep ownership within this context: prompt coroutine cancellation must not leak a decoded bitmap.
        return withContext(Dispatchers.IO) {
            val image=decodeRegion(resolver,uri,grid,crop)
            try {
                withContext(Dispatchers.Default) { quantize(image,grid,progress,options) }
            } finally { image.bitmap.recycle() }
        }
    }

    internal suspend fun quantize(image: WorkingImage, grid: GridSize, progress: (Float)->Unit,
        options: ConversionOptions = ConversionOptions()): Pattern {
        val width=image.bitmap.width; val height=image.bitmap.height
        val pixels=IntArray(width*height)
        image.bitmap.getPixels(pixels,0,width,0,0,width,height)
        val context=currentCoroutineContext()
        return PatternGenerator.generate(pixels,width,height,image.crop,grid,options,{ context.ensureActive() },progress)
    }
}
