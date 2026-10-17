package dev.pindou.pattern

import dev.pindou.pattern.pattern.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

class FullPaletteTest {
    @Test fun bundledSourceMatchesAll291Constants() {
        val stream=javaClass.classLoader!!.getResourceAsStream("palettes/mard-291.csv")!!
        val bytes=stream.use { it.readBytes() }
        val rows=bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.map { it.split(',') }.toList()
        assertEquals(291,rows.size)
        assertEquals(291,rows.map { it[0] }.toSet().size)
        val byCode=Mard.palette.colors.associateBy { it.code }
        rows.forEach { row ->
            val rgb=(row[2].toInt() shl 16) or (row[3].toInt() shl 8) or row[4].toInt()
            assertEquals(row[0],rgb,byCode.getValue(row[0]).displayRgb)
        }
        val metadata=javaClass.classLoader!!.getResourceAsStream("palettes/mard-291-source.json")!!.bufferedReader().use { it.readText() }
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertTrue(metadata.contains("\"bundled_sha256\": \"$hash\""))
        assertTrue(metadata.contains(Mard.COMMIT))
        assertTrue(javaClass.classLoader!!.getResourceAsStream("licenses/beadcolors-MIT.txt")!!.bufferedReader().use { it.readText() }.contains("Copyright (c) 2020 maxcleme"))
    }
    @Test fun allFamiliesAndNaturalOrderArePresent() {
        val actual=Mard.palette.colors.groupingBy { it.code.takeWhile(Char::isLetter) }.eachCount()
        assertEquals(mapOf("A" to 26,"B" to 32,"C" to 29,"D" to 26,"E" to 24,"F" to 25,"G" to 21,
            "H" to 23,"M" to 15,"P" to 23,"Q" to 5,"R" to 28,"T" to 1,"Y" to 5,"ZG" to 8),actual)
        assertEquals("A2",Mard.palette.colors[1].code)
        assertEquals("ZG8",Mard.palette.colors.last().code)
    }
    @Test fun highIndicesDoNotOverflowOrWrap() {
        val values=intArrayOf(0,127,128,255,256,290)
        val pattern=Pattern(GridSize(8,8),IntArray(64) { values[it%values.size] })
        for(i in 0 until 64) assertEquals(values[i%values.size],pattern[i])
        assertEquals(64,pattern.counts().sum())
        assertEquals(6,pattern.usedColors().size)
        val last=Mard.palette.colors[290]
        assertEquals(290,Mard.nearest(ColorMath.rgbToLab(last.displayRgb)))
    }
    @Test fun duplicateRgbKeepsBothCodesAndHasStableTieBreak() {
        val duplicates=Mard.palette.colors.withIndex().groupBy { it.value.displayRgb }.values.filter { it.size>1 }
        assertTrue(duplicates.isNotEmpty())
        for(group in duplicates) {
            val lab=ColorMath.rgbToLab(group.first().value.displayRgb)
            repeat(3) { assertEquals(group.first().index,Mard.nearest(lab)) }
        }
    }
    @Test fun exactMemoizationDoesNotChangeSelection() {
        val matcher=PaletteMatcher()
        for(rgb in listOf(0xDFB356,0x427ECC,0x80A563,0xF5E8D1,0x413628)) {
            val lab=ColorMath.rgbToLab(rgb)
            repeat(3) { assertEquals(Mard.nearest(lab),matcher.nearest(lab)) }
        }
    }
    @Test fun warmAndSaturatedFixtureUsesMoreThanThreeColors() {
        val codes=listOf("A6","A26","B5","C5","D5","F5","G5","E14","H4")
        val selected=codes.map { Mard.nearest(ColorMath.rgbToLab(Mard.palette.colors[Mard.indexOf(it)].displayRgb)) }
        assertEquals(codes.size,selected.toSet().size)
        assertTrue(selected.contains(Mard.indexOf("G5")))
        // The former white/skin/black set cannot preserve these palette-exact colors.
        val old=listOf(0xFBFBFB,0xFBF4EC,0x010101).map(ColorMath::rgbToLab)
        var before=0.0; var after=0.0
        codes.forEachIndexed { i,code ->
            val lab=Mard.labs[Mard.indexOf(code)]
            before+=old.minOf { ColorMath.deltaE00(lab,it) }
            after+=ColorMath.deltaE00(lab,Mard.labs[selected[i]])
        }
        assertEquals(0.0,after,1e-9)
        assertTrue(before>50.0)
    }
    @Test fun statisticsAreDefensiveAndOnlyUsedColorsAreListed() {
        val index=Mard.indexOf("G5")
        val pattern=Pattern(GridSize(8,8),IntArray(64) { index })
        pattern.counts()[index]=0
        assertEquals(64,pattern.counts()[index])
        assertEquals(listOf("G5"),pattern.usedColors().map { it.code })
        assertEquals(64,pattern.usedColors().sumOf { it.count })
    }
    @Test(expected=IllegalArgumentException::class) fun pendingIndexRejected() {
        Pattern(GridSize(8,8),IntArray(64) { -2 })
    }
    @Test fun all291ColorsFitThePngLegend() {
        val pattern=Pattern(GridSize(128,128),IntArray(16384) { it%291 })
        val used=pattern.usedColors()
        assertEquals(291,used.size); assertEquals(16384,used.sumOf { it.count })
        val layout=ExportLayout(pattern.grid,used.size)
        assertTrue(layout.pixelCount<=25_000_000)
        for(i in used.indices) {
            assertTrue(layout.legendX(i)>=layout.margin)
            assertTrue(layout.legendX(i)+104<=layout.width-layout.margin)
            assertTrue(layout.legendY(i)+24<layout.footerTop)
        }
        assertTrue(layout.footerTop+56<layout.height)
    }
    @Test fun narrowAndWideGridExportsFit() {
        for(grid in listOf(GridSize(8,8),GridSize(8,128),GridSize(128,8),GridSize(29,29))) {
            val layout=ExportLayout(grid,minOf(291,grid.rows*grid.columns))
            assertTrue(layout.width>=grid.columns*24+48)
            assertTrue(layout.legendTop>layout.gridTop+grid.rows*24)
            assertTrue(layout.pixelCount<=25_000_000)
        }
    }
    @Test fun blackOrWhiteLabelsHaveReadableContrastOnEveryColor() {
        for(color in Mard.palette.colors) {
            val bg=BeadContrast.luminance(color.displayRgb)
            val fg=BeadContrast.luminance(BeadContrast.textRgb(color.displayRgb))
            assertTrue(color.code,(max(bg,fg)+.05)/(min(bg,fg)+.05)>=4.5)
        }
    }
}
