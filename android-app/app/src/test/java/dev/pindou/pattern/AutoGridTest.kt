package dev.pindou.pattern

import dev.pindou.pattern.chartimport.*
import dev.pindou.pattern.pattern.GridSize
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.CancellationException
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class AutoGridTest {
    private fun source()=javaClass.getResourceAsStream("/charts/mard-51x52.jpeg")!!.use { ImageIO.read(it) }
    private fun detect(image: BufferedImage): AutoGridResult? = AutoGridDetector.detect(image.getRGB(0,0,image.width,image.height,null,0,image.width),image.width,image.height)
    @Test fun actualUserChartFinds51Columns52RowsAndExcludesLegend() {
        val image=source();val result=detect(image);assertNotNull(result)
        val s=result!!.primary;println("USER_GRID ${s.grid} ROI ${s.relativeRegion} alternate ${result.withCoordinateBands?.grid}")
        assertEquals(GridSize(51,52),s.grid)
        val r=s.relativeRegion
        assertEquals(27.5,r.left*image.width.toDouble(),3.0);assertEquals(103.5,r.top*image.height.toDouble(),3.0)
        assertEquals(1405.5,r.right*image.width.toDouble(),3.0);assertEquals(1509.0,r.bottom*image.height.toDouble(),3.0)
        assertNotNull(result.withCoordinateBands)
    }
    @Test fun userChartPaddingDoesNotHardcodeGridOrigin() {
        val original=source();val padded=BufferedImage(original.width+48,original.height+42,BufferedImage.TYPE_INT_RGB)
        padded.createGraphics().apply { color=Color.WHITE;fillRect(0,0,padded.width,padded.height);drawImage(original,16,21,null);dispose() }
        val s=detect(padded)!!.primary;println("PADDED ${s.grid} ${s.relativeRegion}");assertEquals(GridSize(51,52),s.grid)
        assertEquals(43.5,s.relativeRegion.left*padded.width.toDouble(),4.0);assertEquals(124.5,s.relativeRegion.top*padded.height.toDouble(),4.0)
    }
    @Test fun resizedChartUsesSubpixelPitchInsteadOfBoldLinePeriod() {
        val original=source();val scaled=BufferedImage(1146,1536,BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply {setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);drawImage(original,0,0,scaled.width,scaled.height,null);dispose()}
        val s=detect(scaled)!!.primary;println("SCALED ${s.grid} ${s.relativeRegion}");assertEquals(GridSize(51,52),s.grid)
    }
    @Test fun blankImageHasNoFabricatedGrid() { assertNull(AutoGridDetector.detect(IntArray(256*256) {-1},256,256)) }
    @Test(expected=CancellationException::class) fun detectorChecksCancellation() {
        AutoGridDetector.detect(IntArray(256*256),256,256) { throw CancellationException() }
    }
    @Test fun synthetic8to128GridsDoNotChooseBoldFiveCellPeriod() {
        for(n in listOf(8,29,64,128)) {
            val pitch=12;val margin=18;val w=n*pitch+margin*2;val h=w+40;val p=IntArray(w*h) {-1}
            for(y in 0..n*pitch) for(x in 0..n*pitch) {
                val line=x%pitch==0||y%pitch==0
                p[(y+margin)*w+x+margin]=if(line) 0xFF555555.toInt() else if((x/pitch+y/pitch)%2==0) 0xFFE0E8DC.toInt() else -1
            }
            val s=AutoGridDetector.detect(p,w,h)!!.primary;println("SYNTHETIC $n ${s.grid} ${s.relativeRegion}");assertEquals(GridSize(n,n),s.grid)
        }
    }
}
