package dev.pindou.pattern

import dev.pindou.pattern.chartimport.*
import dev.pindou.pattern.pattern.*
import dev.pindou.pattern.project.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class ChartImportTest {
    private val grid=GridSize(8,8)
    private val red=Mard.indexOf("F5")
    private fun evidence()=List(64) { CellEvidence(red,0xAA2244,.7f,"") }
    private fun draft(value: Int)=RecognitionDraft(grid,IntArray(64) {value},evidence())
    private fun project(d: RecognitionDraft?=draft(PENDING_CELL))=ChartProject("测试工程","a".repeat(64),grid,ChartRegion(.1f,.1f,.9f,.8f),ChartRegion(.1f,.85f,.9f,1f),listOf(LegendEntry(0xAA2244,"F5",true)),d)
    @Test fun emptyCellsAreNotWhiteBeads() {
        val p=Pattern(grid,IntArray(64) { if(it%2==0) -1 else Mard.indexOf("H2") })
        assertEquals(32,p.emptyCount);assertEquals(32,p.beadCount);assertEquals(32,p.counts().sum());assertEquals(1,p.usedColors().size)
    }
    @Test fun allEmptyChartCanExportGeometryWithoutLegend() {
        val p=Pattern(grid,IntArray(64) {-1});assertEquals(0,p.beadCount);assertTrue(p.usedColors().isEmpty());assertTrue(ExportLayout(grid,0).height>0)
    }
    @Test(expected=IllegalStateException::class) fun pendingCannotConfirm() { draft(PENDING_CELL).confirm() }
    @Test fun editsAreImmutableAndEmptySurvivesConfirmation() {
        val old=draft(PENDING_CELL);val d=old.change(IntArray(64) {it},EMPTY_CELL)
        assertEquals(64,old.pendingCount);assertEquals(0,d.pendingCount);assertEquals(64,d.confirm().emptyCount)
        d.snapshot()[0]=0;assertEquals(-1,d[0])
    }
    @Test fun historyHandlesBatchUndoRedoAndBranch() {
        val h=DraftHistory();val a=draft(PENDING_CELL);val b=a.change(intArrayOf(0,5,63),red);h.record(a,b)
        assertArrayEquals(a.snapshot(),h.undo(b).snapshot());assertArrayEquals(b.snapshot(),h.redo(a).snapshot())
        h.undo(b);val c=a.change(intArrayOf(2),-1);h.record(a,c);assertFalse(h.canRedo)
    }
    @Test fun backgroundIgnoresCenteredBlackAndWhiteLabels() {
        for(bg in listOf(0xFFFFFF,0x000000,0xFDAD49)) {
            val pixels=IntArray(400) { bg or (0xFF shl 24) }
            for(y in 8..11) for(x in 6..13) pixels[y*20+x]=(if(bg==0x000000) 0xFFFFFF else 0x000000) or (0xFF shl 24)
            for(i in 0..19) { pixels[i]=0xFF888888.toInt();pixels[i*20]=0xFF888888.toInt() }
            assertEquals(bg,CellRecognizer.background(pixels,20,20,CropRect(0.0,0.0,20.0,20.0)).rgb)
        }
    }
    @Test fun userConfirmedLegendOverridesNearestMardRgb() {
        val pixels=IntArray(160*160) { 0xFF220000.toInt() }
        val d=CellRecognizer.recognize(pixels,160,160,grid,listOf(LegendEntry(0x220000,"F5",true)))
        assertEquals(0,d.pendingCount);assertTrue(d.snapshot().all { it==red })
    }
    @Test fun whiteAlwaysRequiresExplicitEmptyOrBeadChoice() {
        val d=CellRecognizer.recognize(IntArray(160*160) {-1},160,160,grid,listOf(LegendEntry(0xFFFFFF,"H2",true)))
        assertEquals(64,d.pendingCount);assertTrue(d.evidence.all { it.suggested==Mard.indexOf("H2") })
    }
    @Test fun duplicateDisplayColorsStayUncertain() {
        val d=CellRecognizer.recognize(IntArray(160*160) {0xFFFFEBFA.toInt()},160,160,grid,listOf(LegendEntry(0xFFEBFA,"Q4",true),LegendEntry(0xFFEBFA,"R11",true)))
        assertEquals(64,d.pendingCount)
    }
    @Test(expected=IllegalArgumentException::class) fun unconfirmedLegendCannotDriveRecognition() {
        CellRecognizer.recognize(IntArray(64) {-1},8,8,grid,listOf(LegendEntry(0xFFFFFF,"H2",false)))
    }
    @Test fun recognitionCancellationDoesNotReturnDraft() {
        var checks=0;var returned=false
        try { CellRecognizer.recognize(IntArray(6400) {0xFF220000.toInt()},80,80,grid,listOf(LegendEntry(0x220000,"F5",true)),{ if(++checks>5) throw CancellationException() });returned=true } catch(_: CancellationException) {}
        assertFalse(returned)
    }
    @Test fun syntheticGridsRecoverRowMajorCellsAtAllSizes() {
        val codes=listOf("F5","B5","C5","G5");val entries=codes.map { LegendEntry(Mard.palette.colors[Mard.indexOf(it)].displayRgb,it,true) }
        for(n in listOf(8,29,64,128)) {
            val cell=12;val w=n*cell;val pixels=IntArray(w*w)
            for(y in 0 until w) for(x in 0 until w) {
                val i=((y/cell)*n+x/cell)%4
                pixels[y*w+x]=when { x%cell==0 || y%cell==0 -> 0xFF999999.toInt();x%cell in 5..6 && y%cell in 5..6 -> -1;else -> entries[i].rgb or (0xFF shl 24) }
            }
            val d=CellRecognizer.recognize(pixels,w,w,GridSize(n,n),entries)
            for(i in 0 until n*n) assertEquals("size=$n index=$i",Mard.indexOf(codes[i%4]),d.evidence[i].suggested)
            val final=d.acceptSuggestions().confirm();assertEquals(n*n,final.beadCount)
        }
    }
    @Test fun gridDetectorRejectsFlatImage() { assertNull(GridDetector.suggest(IntArray(160*160) {-1},160,160)) }
    @Test fun gridDetectorFindsSimpleRepeatedLines() {
        val pixels=IntArray(160*160) { if(it%160%20==0 || it/160%20==0) 0xFF777777.toInt() else -1 }
        val result=GridDetector.suggest(pixels,160,160);assertNotNull(result);assertEquals(GridSize(8,8),result!!.grid)
    }
    @Test fun projectRoundTripRestoresPendingEmptyAndLegend() {
        val d=draft(PENDING_CELL).change(intArrayOf(1),EMPTY_CELL).change(intArrayOf(2),red)
        val p=project(d);val restored=ProjectCodec.decode(ProjectCodec.encode(p))
        assertEquals(p.region,restored.region);assertEquals(p.legend,restored.legend);assertEquals(p.sourceHash,restored.sourceHash)
        assertArrayEquals(d.snapshot(),restored.draft!!.snapshot());assertEquals(d.evidence,restored.draft.evidence)
    }
    @Test fun calibrationOnlyProjectRoundTrips() { val p=ProjectCodec.decode(ProjectCodec.encode(project(null)));assertNull(p.draft) }
    @Test(expected=IllegalArgumentException::class) fun corruptProjectRejected() { val bytes=ProjectCodec.encode(project());bytes[10]=(bytes[10].toInt() xor 1).toByte();ProjectCodec.decode(bytes) }
    @Test(expected=IllegalArgumentException::class) fun truncatedProjectRejected() { val b=ProjectCodec.encode(project());ProjectCodec.decode(b.copyOf(b.size-10)) }
    @Test(expected=IllegalArgumentException::class) fun invalidRegionRejected() { ChartRegion(.7f,.1f,.3f,.8f) }
    @Test fun confirmedSnapshotDoesNotFollowFurtherDraftChanges() {
        val a=draft(red);val pattern=a.confirm();a.change(intArrayOf(0),-1);assertEquals(red,pattern[0])
    }
}
