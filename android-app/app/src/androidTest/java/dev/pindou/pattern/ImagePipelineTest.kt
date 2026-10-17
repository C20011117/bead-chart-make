package dev.pindou.pattern

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pindou.pattern.image.ImagePipeline
import dev.pindou.pattern.export.PatternRenderer
import dev.pindou.pattern.pattern.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImagePipelineTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver get()=context.contentResolver
    private fun fixture(format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): File {
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val colors=intArrayOf(0xFFFFFFFF.toInt(),0xFFFDAD49.toInt(),0xFF000000.toInt(),0)
        for(y in 0..31) for(x in 0..31) bitmap.setPixel(x,y,colors[(y/16)*2+x/16])
        val file=File.createTempFile("bead-fixture-",if(format==Bitmap.CompressFormat.PNG) ".png" else ".jpg",context.cacheDir)
        try { file.outputStream().use { assertTrue(bitmap.compress(format,100,it)) } } finally { bitmap.recycle() }
        return file
    }
    @Test fun pngQuadrantsAlphaAndExportAreConsistent() = runBlocking {
        val input=fixture(); val output=File.createTempFile("bead-export-",".png",context.cacheDir)
        try {
            val pattern=ImagePipeline.generate(resolver,Uri.fromFile(input),GridSize(8,8),CropSpec()) {}
            assertEquals(32,pattern.counts()[Mard.indexOf("H2")])
            assertEquals(16,pattern.counts()[Mard.indexOf("A6")])
            assertEquals(16,pattern.counts()[Mard.indexOf("H7")])
            PatternRenderer.save(resolver,Uri.fromFile(output),pattern)
            val bitmap=BitmapFactory.decodeFile(output.absolutePath)
            try {
                val layout=ExportLayout(pattern.grid,pattern.usedColors().size)
                assertEquals(layout.width,bitmap.width); assertEquals(layout.height,bitmap.height)
                for(y in 0..7) for(x in 0..7) {
                    val expected=Mard.palette.colors[pattern[y*8+x]].displayRgb or (0xFF shl 24)
                    assertEquals(expected,bitmap.getPixel(layout.gridLeft+x*layout.cell+3,layout.gridTop+y*layout.cell+3))
                }
            } finally { bitmap.recycle() }
        } finally { input.delete(); output.delete() }
    }
    @Test fun allExifOrientationsMoveQuadrantsCorrectly() = runBlocking {
        for(orientation in 1..8) {
            val input=fixture(Bitmap.CompressFormat.JPEG)
            try {
                ExifInterface(input).apply { setAttribute(ExifInterface.TAG_ORIENTATION,orientation.toString()); saveAttributes() }
                val pattern=ImagePipeline.generate(resolver,Uri.fromFile(input),GridSize(8,8),CropSpec()) {}
                // JPEG has no transparency; use the three opaque quadrant centers.
                listOf(Triple(.25,.25,Mard.indexOf("H2")),Triple(.75,.25,Mard.indexOf("A6")),Triple(.25,.75,Mard.indexOf("H7"))).forEach { (x,y,index) ->
                    val mapped=ExifGeometry.point(x,y,orientation)
                    assertEquals("EXIF $orientation",index,pattern[(mapped.second*8).toInt()*8+(mapped.first*8).toInt()])
                }
                val preview=ImagePipeline.decode(resolver,Uri.fromFile(input))
                try { assertEquals(32,preview.width); assertEquals(32,preview.height) } finally { preview.recycle() }
            } finally { input.delete() }
        }
    }
    @Test fun exportContainsAll291LegendSwatchesAndHighIndices() = runBlocking {
        val pattern=Pattern(GridSize(16,19),IntArray(304) { it%291 })
        val file=File.createTempFile("bead-all-colors-",".png",context.cacheDir)
        try {
            PatternRenderer.save(resolver,Uri.fromFile(file),pattern)
            val bitmap=BitmapFactory.decodeFile(file.absolutePath)
            try {
                val used=pattern.usedColors(); val layout=ExportLayout(pattern.grid,used.size)
                assertEquals(291,used.size); assertEquals(layout.height,bitmap.height)
                used.forEachIndexed { i,color ->
                    assertEquals(color.code,color.displayRgb or (0xFF shl 24),bitmap.getPixel(layout.legendX(i)+5,layout.legendY(i)+8))
                }
                for(index in listOf(127,128,255,256,290)) {
                    val rgb=Mard.palette.colors[index].displayRgb or (0xFF shl 24)
                    assertEquals(rgb,bitmap.getPixel(layout.gridLeft+(index%16)*layout.cell+3,layout.gridTop+(index/16)*layout.cell+3))
                }
            } finally { bitmap.recycle() }
        } finally { file.delete() }
    }
    @Test fun canceledGenerationDoesNotPublish() = runBlocking {
        val input=fixture()
        try {
            var published=false
            val job=launch(start=CoroutineStart.UNDISPATCHED) {
                ImagePipeline.generate(resolver,Uri.fromFile(input),GridSize(128,128),CropSpec()) {}
                published=true
            }
            job.cancelAndJoin()
            assertFalse(published)
        } finally { input.delete() }
    }
}
