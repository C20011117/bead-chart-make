package dev.pindou.pattern

import dev.pindou.pattern.pattern.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class IllustrationTest {
    private fun rect(n: Int)=CropRect(0.0,0.0,n.toDouble(),n.toDouble())
    @Test fun supportedThinBlackStrokeSurvivesInsteadOfGrayAverage() {
        val pixels=IntArray(100) { if(it%10<2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val sample=IllustrationSampler().sample(pixels,10,10,rect(10))
        assertEquals(Mard.indexOf("H7"),Mard.nearest(sample.lab()))
        assertNotEquals(Mard.indexOf("H7"),Mard.nearest(AreaSampler.sample(pixels,10,10,rect(10))))
    }
    @Test fun isolatedBlackSpeckDoesNotBlackenCell() {
        val pixels=IntArray(100) { 0xFFFFFFFF.toInt() };pixels[50]=0xFF000000.toInt()
        assertEquals(Mard.indexOf("H2"),Mard.nearest(IllustrationSampler().sample(pixels,10,10,rect(10)).lab()))
    }
    @Test fun uniformPaletteColorsRemainExactIncludingHighIndices() {
        for(index in listOf(0,127,128,255,256,290)) {
            val rgb=Mard.palette.colors[index].displayRgb
            val sample=IllustrationSampler().sample(IntArray(100) { rgb or (0xFF shl 24) },10,10,rect(10))
            assertEquals(Mard.nearest(ColorMath.rgbToLab(rgb)),Mard.nearest(sample.lab()))
        }
    }
    @Test fun transparencyCompositesOverWhite() {
        val sample=IllustrationSampler().sample(IntArray(100) { 0x000000FF },10,10,rect(10))
        assertEquals(Mard.indexOf("H2"),Mard.nearest(sample.lab()))
    }
    @Test fun samplerReuseDoesNotCarryPreviousBins() {
        val sampler=IllustrationSampler()
        sampler.sample(IntArray(100) { 0xFF000000.toInt() },10,10,rect(10))
        assertEquals(Mard.indexOf("H2"),Mard.nearest(sampler.sample(IntArray(100) { -1 },10,10,rect(10)).lab()))
    }
    @Test fun narrowLightStrokeBetweenDarkSidesSurvives() {
        val pixels=IntArray(100) { if(it/10 in 4..5) -1 else 0xFF000000.toInt() }
        assertEquals(Mard.indexOf("H2"),Mard.nearest(IllustrationSampler().sample(pixels,10,10,rect(10)).lab()))
    }
    @Test fun whiteSpeckDoesNotTurnBlackRegionWhite() {
        val pixels=IntArray(100) { 0xFF000000.toInt() };pixels[44]=-1
        assertEquals(Mard.indexOf("H7"),Mard.nearest(IllustrationSampler().sample(pixels,10,10,rect(10)).lab()))
    }
    @Test fun broadWhiteBoundaryIsNotMistakenForNarrowLightStroke() {
        val pixels=IntArray(100) { if(it%10<5) -1 else 0xFF000000.toInt() }
        val linear=DoubleArray(256) { ColorMath.linear(it/255.0) }
        assertNull(LightStrokeSampler.sample(pixels,10,10,rect(10),linear,doubleArrayOf(1.0,1.0,1.0)))
    }
    @Test fun multipleParallelLightLinesDoNotBecomeOneWhiteBand() {
        val pixels=IntArray(400) { if(it/20==6 || it/20==12) -1 else 0xFF000000.toInt() }
        val linear=DoubleArray(256) { ColorMath.linear(it/255.0) }
        assertNull(LightStrokeSampler.sample(pixels,20,20,rect(20),linear,doubleArrayOf(1.0,1.0,1.0)))
    }
    @Test fun lightStrokeKeepsSourceColorInsteadOfForcedWhite() {
        val rgb=0xF5F3EC
        val pixels=IntArray(100) { if(it/10 in 4..5) rgb or (0xFF shl 24) else 0xFF000000.toInt() }
        val lab=IllustrationSampler().sample(pixels,10,10,rect(10)).lab()
        assertEquals(0.0,ColorMath.deltaE00(ColorMath.rgbToLab(rgb),lab),1e-7)
    }
    @Test fun photoModeIsExactlyAreaSampling() {
        val pixels=IntArray(1024) { i -> 0xFF000000.toInt() or ((i*17777) and 0xFFFFFF) }
        val grid=GridSize(8,8)
        val pattern=PatternGenerator.generate(pixels,32,32,rect(32),grid,ConversionOptions(SamplingStyle.PHOTO))
        for(y in 0..7) for(x in 0..7) assertEquals(Mard.nearest(AreaSampler.sample(pixels,32,32,CropRect(x*4.0,y*4.0,4.0,4.0))),pattern[y*8+x])
    }
    @Test fun progressIsMonotonicAndCountsCompleteInBothModes() {
        for(style in SamplingStyle.entries) {
            var previous=0f
            val pattern=PatternGenerator.generate(IntArray(64) { -1 },8,8,rect(8),GridSize(8,8),ConversionOptions(style),{}, {
                assertTrue(it>=previous && it<=1f);previous=it
            })
            assertEquals(1f,previous,0f);assertEquals(64,pattern.counts().sum())
        }
    }
    @Test fun cancellationStopsBothPasses() {
        for(stop in listOf(1,20)) {
            var checks=0;var canceled=false
            try { PatternGenerator.generate(IntArray(64) { -1 },8,8,rect(8),GridSize(8,8),ConversionOptions(),{
                if(++checks==stop) throw CancellationException()
            }) } catch(_: CancellationException) { canceled=true }
            assertTrue(canceled);assertEquals(stop,checks)
        }
    }
    @Test fun fitPreservesFullOriginalWithWhitePadding() {
        val bounds=cropRect(8,16,GridSize(8,8),CropSpec(fitImage=true))
        assertEquals(CropRect(-4.0,0.0,16.0,16.0),bounds)
        for(style in SamplingStyle.entries) {
            val pattern=PatternGenerator.generate(IntArray(128) { 0xFF000000.toInt() },8,16,bounds,GridSize(8,8),ConversionOptions(style))
            assertEquals(32,pattern.counts()[Mard.indexOf("H2")]);assertEquals(32,pattern.counts()[Mard.indexOf("H7")])
        }
    }
    @Test fun fourRulersAndCompactLegendFitLargestExport() {
        val layout=ExportLayout(GridSize(128,128),291)
        assertEquals(layout.margin+layout.cell,layout.gridLeft)
        assertTrue(layout.width>=layout.gridLeft+129*layout.cell)
        assertTrue(layout.legendTop>layout.gridTop+129*layout.cell)
        assertTrue(layout.pixelCount<=25_000_000)
        for(i in 0..290) { assertTrue(layout.legendX(i)+104<=layout.width-layout.margin);assertTrue(layout.legendY(i)+40<layout.footerTop) }
    }
}
