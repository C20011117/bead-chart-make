package dev.pindou.pattern

import dev.pindou.pattern.pattern.*
import org.junit.Assert.*
import org.junit.Test

class PatternTest {
    @Test fun paletteHas291ColorsAndEveryRgbRoundTrips() {
        assertEquals(291,Mard.palette.colors.size)
        Mard.palette.colors.forEach { color ->
            val selected=Mard.palette.colors[Mard.nearest(ColorMath.rgbToLab(color.displayRgb))]
            assertEquals(color.displayRgb,selected.displayRgb)
        }
    }
    @Test fun immutableAndCounted() {
        val cells=IntArray(64) { it%3 }; val pattern=Pattern(GridSize(8,8),cells)
        cells[0]=2; assertEquals(0,pattern[0]); pattern.snapshot()[0]=2; assertEquals(0,pattern[0])
        assertEquals(64,pattern.counts().sum())
    }
    @Test(expected=IllegalArgumentException::class) fun invalidGrid() { GridSize(129,8) }
    @Test(expected=IllegalArgumentException::class) fun invalidCells() { Pattern(GridSize(8,8),IntArray(64) { 291 }) }
    @Test fun transparentMapsToH2() {
        assertEquals(Mard.backgroundIndex,Mard.nearest(AreaSampler.sample(intArrayOf(0x00FF0000),1,1,CropRect(0.0,0.0,1.0,1.0))))
    }
    @Test fun areaMeanIsLinearAndWeighted() {
        val sampled=AreaSampler.sample(intArrayOf(0xFF000000.toInt(),0xFFFFFFFF.toInt()),2,1,CropRect(.5,0.0,1.5,1.0))
        val expected=ColorMath.linearToLab(2.0/3,2.0/3,2.0/3)
        assertEquals(expected.l,sampled.l,1e-9)
    }
    @Test fun cropMaintainsAspectAndBounds() {
        val rect=cropRect(6000,4000,GridSize(29,64),CropSpec(0f,1f,2f))
        assertEquals(29.0/64,rect.width/rect.height,1e-9)
        assertTrue(rect.left>=0 && rect.top>=0 && rect.left+rect.width<=6000 && rect.top+rect.height<=4000)
    }
    // Sharma, Wu, Dalal supplementary CIEDE2000 reference pairs.
    @Test fun standardDeltaE00Pairs() {
        val pairs=listOf(
            doubleArrayOf(50.0,2.6772,-79.7751,50.0,0.0,-82.7485,2.0425),
            doubleArrayOf(50.0,3.1571,-77.2803,50.0,0.0,-82.7485,2.8615),
            doubleArrayOf(50.0,2.8361,-74.02,50.0,0.0,-82.7485,3.4412),
            doubleArrayOf(50.0,-1.3802,-84.2814,50.0,0.0,-82.7485,1.0),
            doubleArrayOf(50.0,-1.1848,-84.8006,50.0,0.0,-82.7485,1.0),
            doubleArrayOf(50.0,-0.9009,-85.5211,50.0,0.0,-82.7485,1.0),
            doubleArrayOf(50.0,0.0,0.0,50.0,-1.0,2.0,2.3669),
            doubleArrayOf(50.0,-1.0,2.0,50.0,0.0,0.0,2.3669),
            doubleArrayOf(50.0,2.49,-0.001,50.0,-2.49,0.0009,7.1792),
            doubleArrayOf(50.0,2.49,-0.001,50.0,-2.49,0.001,7.1792),
            doubleArrayOf(50.0,2.49,-0.001,50.0,-2.49,0.0011,7.2195),
            doubleArrayOf(50.0,2.49,-0.001,50.0,-2.49,0.0012,7.2195),
            doubleArrayOf(50.0,-0.001,2.49,50.0,0.0009,-2.49,4.8045),
            doubleArrayOf(50.0,-0.001,2.49,50.0,0.001,-2.49,4.8045),
            doubleArrayOf(50.0,-0.001,2.49,50.0,0.0011,-2.49,4.7461),
            doubleArrayOf(50.0,2.5,0.0,50.0,0.0,-2.5,4.3065),
            doubleArrayOf(50.0,2.5,0.0,73.0,25.0,-18.0,27.1492),
            doubleArrayOf(50.0,2.5,0.0,61.0,-5.0,29.0,22.8977),
            doubleArrayOf(50.0,2.5,0.0,56.0,-27.0,-3.0,31.903),
            doubleArrayOf(50.0,2.5,0.0,58.0,24.0,15.0,19.4535),
            doubleArrayOf(50.0,2.5,0.0,50.0,3.1736,0.5854,1.0),
            doubleArrayOf(50.0,2.5,0.0,50.0,3.2972,0.0,1.0),
            doubleArrayOf(50.0,2.5,0.0,50.0,1.8634,0.5757,1.0),
            doubleArrayOf(50.0,2.5,0.0,50.0,3.2592,0.335,1.0),
            doubleArrayOf(60.2574,-34.0099,36.2677,60.4626,-34.1751,39.4387,1.2644),
            doubleArrayOf(63.0109,-31.0961,-5.8663,62.8187,-29.7946,-4.0864,1.263),
            doubleArrayOf(61.2901,3.7196,-5.3901,61.4292,2.248,-4.962,1.8731),
            doubleArrayOf(35.0831,-44.1164,3.7933,35.0232,-40.0716,1.5901,1.8645),
            doubleArrayOf(22.7233,20.0904,-46.694,23.0331,14.973,-42.5619,2.0373),
            doubleArrayOf(36.4612,47.858,18.3852,36.2715,50.5065,21.2231,1.4146),
            doubleArrayOf(90.8027,-2.0831,1.441,91.1528,-1.6435,0.0447,1.4441),
            doubleArrayOf(90.9257,-0.5406,-0.9208,88.6381,-0.8985,-0.7239,1.5381),
            doubleArrayOf(6.7747,-0.2908,-2.4247,5.8714,-0.0985,-2.2286,0.6377),
            doubleArrayOf(2.0776,0.0795,-1.135,0.9033,-0.0636,-0.5514,0.9082)
        )
        for(p in pairs) {
            val a=Lab(p[0],p[1],p[2]); val b=Lab(p[3],p[4],p[5])
            assertEquals(p[6],ColorMath.deltaE00(a,b),.0001)
            assertEquals(ColorMath.deltaE00(a,b),ColorMath.deltaE00(b,a),1e-9)
        }
    }
    @Test fun allExifTransformsAreInvertible() {
        for(o in 1..8) {
            val mapped=ExifGeometry.point(.2,.7,o)
            val original=ExifGeometry.point(mapped.first,mapped.second,ExifGeometry.inverse(o))
            assertEquals(.2,original.first,1e-12); assertEquals(.7,original.second,1e-12)
            val rect=CropRect(.1,.2,.3,.4)
            val back=ExifGeometry.rect(ExifGeometry.rect(rect,o),ExifGeometry.inverse(o))
            assertEquals(rect.left,back.left,1e-12); assertEquals(rect.top,back.top,1e-12)
            assertEquals(rect.width,back.width,1e-12); assertEquals(rect.height,back.height,1e-12)
        }
    }
    @Test fun knownQuadrantsAndRepeatability() {
        val pixels=intArrayOf(0xFFFFFFFF.toInt(),0xFFFDAD49.toInt(),0xFF000000.toInt(),0)
        fun generate()=IntArray(64) { i ->
            Mard.nearest(AreaSampler.sample(pixels,2,2,CropRect((i%8)/4.0,(i/8)/4.0,.25,.25)))
        }
        val first=generate(); assertArrayEquals(first,generate())
        val counts=Pattern(GridSize(8,8),first).counts()
        assertEquals(32,counts[Mard.indexOf("H2")]); assertEquals(16,counts[Mard.indexOf("A6")]); assertEquals(16,counts[Mard.indexOf("H7")])
        assertEquals(64,counts.sum())
        assertEquals(Mard.indexOf("H2"),first[0]); assertEquals(Mard.indexOf("A6"),first[7])
        assertEquals(Mard.indexOf("H7"),first[56]); assertEquals(Mard.indexOf("H2"),first[63])
    }
    @Test fun fractionalAlphaIsCompositedInLinearLight() {
        val sampled=AreaSampler.sample(intArrayOf(0x80000000.toInt()),1,1,CropRect(0.0,0.0,1.0,1.0))
        val white=ColorMath.linear((Mard.backgroundRgb and 255)/255.0)*(1-128.0/255)
        assertEquals(ColorMath.linearToLab(white,white,white).l,sampled.l,1e-9)
    }
    @Test(expected=IllegalArgumentException::class) fun wrongMatrixLength() { Pattern(GridSize(8,8),IntArray(63)) }
    @Test(expected=IllegalArgumentException::class) fun badScale() { CropSpec(scale=Float.NaN) }
    @Test fun maximumGridCounts() { assertEquals(16384,Pattern(GridSize(128,128),IntArray(16384)).counts().sum()) }

}
