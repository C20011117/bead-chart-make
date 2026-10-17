package dev.pindou.pattern

import android.graphics.*
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pindou.pattern.chartimport.*
import dev.pindou.pattern.pattern.*
import dev.pindou.pattern.image.ChartImageDecoder
import dev.pindou.pattern.export.PatternRenderer
import dev.pindou.pattern.project.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChartImportDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun input(): File {
        val bitmap=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(Color.BLUE);drawRect(0f,0f,100f,100f,Paint().apply {color=Color.RED}) }
        val f=File.createTempFile("chart-import-",".png",context.cacheDir)
        try { f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
        return f
    }
    @Test fun arbitraryRoiDoesNotForceSquareAspect() {
        val file=input()
        try {
            val bitmap=ChartImageDecoder.decode(file,ChartRegion(0f,0f,1f,.25f))
            try { assertEquals(100,bitmap.width);assertEquals(50,bitmap.height);assertEquals(Color.RED,bitmap.getPixel(50,25)) } finally { bitmap.recycle() }
        } finally { file.delete() }
    }
    @Test fun detailRoiDecodeHonorsPixelBudget() {
        val file=input()
        try {
            val bitmap=ChartImageDecoder.decode(file,ChartRegion(),maxEdge=64)
            try { assertTrue(bitmap.width<=64);assertTrue(bitmap.height<=64);assertEquals(Color.RED,bitmap.getPixel(bitmap.width/2,bitmap.height/4)) } finally { bitmap.recycle() }
        } finally { file.delete() }
    }
    @Test fun emptyChartPngExportsWithoutCountingWhiteBeads() = runBlocking {
        val p=Pattern(GridSize(8,8),IntArray(64) {-1});val f=File.createTempFile("chart-empty-",".png",context.cacheDir)
        try {
            PatternRenderer.save(context.contentResolver,Uri.fromFile(f),p)
            val bitmap=BitmapFactory.decodeFile(f.path)
            try { val layout=ExportLayout(p.grid,0);assertEquals(layout.width,bitmap.width);assertEquals(0,p.beadCount);assertEquals(64,p.emptyCount) } finally { bitmap.recycle() }
        } finally { f.delete() }
    }
    @Test fun privateCopyAndProjectSurviveNewStoreInstance() {
        val input=input();val store=PatternProjectStore(context);val id=store.import(Uri.fromFile(input)) {}
        try {
            val p=ChartProject("恢复测试",store.hash(id),GridSize(8,8),ChartRegion(),ChartRegion(.1f,.8f,.9f,1f),listOf(LegendEntry(0xFFFFFF,"H2",true)),null,true)
            store.save(id,p);input.delete()
            val restored=PatternProjectStore(context).load(id)
            assertEquals(p,restored);assertTrue(store.source(id).isFile)
        } finally { input.delete();store.folder(id).deleteRecursively() }
    }
}
