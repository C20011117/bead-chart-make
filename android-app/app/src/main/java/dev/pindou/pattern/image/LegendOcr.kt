package dev.pindou.pattern.image

import dev.pindou.pattern.chartimport.*

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.pindou.pattern.pattern.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object LegendOcr {
    private var initialized=false
    @Synchronized private fun initialize(context: android.content.Context) {
        if(!initialized) { com.google.mlkit.common.MlKit.initialize(context.applicationContext);initialized=true }
    }
    // Bundled Latin OCR handles MARD letters/digits on-device. Every result still requires confirmation.
    suspend fun read(context: android.content.Context,bitmap: Bitmap): List<LegendEntry> = suspendCoroutine { continuation ->
        initialize(context)
        val client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        client.process(InputImage.fromBitmap(bitmap,0)).addOnCompleteListener { task ->
            try {
                if(!task.isSuccessful) throw task.exception ?: IllegalStateException("图例文字识别失败")
                val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                val codes=Regex("(?<![A-Z0-9])([A-Z]{1,2})\\s*([0-9]{1,2})(?![0-9])")
                val entries=ArrayList<LegendEntry>()
                for(block in task.result.textBlocks) for(line in block.lines) for(element in line.elements) {
                    val box=element.boundingBox ?: continue
                    val text=element.text.uppercase()
                    for(match in codes.findAll(text)) {
                        val code=match.groupValues[1]+match.groupValues[2]
                        if(Mard.palette.colors.none { it.code==code }) continue
                        val rect=CropRect((box.left-5).coerceAtLeast(0).toDouble(),(box.top-5).coerceAtLeast(0).toDouble(),(box.width()+10).toDouble(),(box.height()+10).toDouble())
                        val rgb=CellRecognizer.background(pixels,bitmap.width,bitmap.height,rect).rgb
                        if(entries.none { it.code==code }) entries+=LegendEntry(rgb,code,false)
                    }
                }
                continuation.resume(entries.take(500))
            } catch(t: Throwable) { continuation.resumeWithException(t) }
            finally { client.close() }
        }
    }
}
