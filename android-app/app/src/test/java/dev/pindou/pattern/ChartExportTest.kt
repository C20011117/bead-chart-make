package dev.pindou.pattern

import dev.pindou.pattern.chartimport.*
import dev.pindou.pattern.pattern.*
import dev.pindou.pattern.project.*
import org.junit.Assert.*
import org.junit.Test

class ChartExportTest {
    private val grid=GridSize(8,8)
    private val white=Mard.indexOf("H2")
    private fun draft(value: Int)=RecognitionDraft(grid,IntArray(64) {value},List(64) {CellEvidence(white,0xFFFFFF,.5f,"白豆/空格")})
    @Test fun legendReviewWithoutCellMatrixExplainsNextStep() {
        val reasons=ChartExport.blockers(true,true,null)
        assertEquals(1,reasons.size);assertTrue(reasons[0].contains("逐格识别"))
    }
    @Test fun brandAndGeometryMustStillBeConfirmed() {
        assertEquals(2,ChartExport.blockers(false,false,draft(white)).size)
    }
    @Test fun pendingWhiteCellsHaveVisibleCountAndCannotExport() {
        assertTrue(ChartExport.blockers(true,true,draft(PENDING_CELL)).single().contains("64"))
        try {ChartExport.snapshot(true,true,draft(PENDING_CELL));fail()} catch(_: IllegalStateException) {}
    }
    @Test fun reviewedDraftExportsWithoutSavingProjectOrPriorPattern() {
        val p=ChartExport.snapshot(true,true,draft(white))
        assertEquals(64,p.beadCount);assertEquals(0,p.emptyCount)
    }
    @Test fun exportSnapshotDoesNotChangeWithLaterEdits() {
        val d=draft(white);val p=ChartExport.snapshot(true,true,d)
        val edited=d.change(intArrayOf(0),EMPTY_CELL)
        assertEquals(white,p[0]);assertEquals(EMPTY_CELL,edited[0]);assertEquals(64,p.beadCount)
    }
    @Test fun emptyCellsStayEmptyNotWhiteDuringExportPreparation() {
        val d=draft(EMPTY_CELL).change(intArrayOf(0),white);val p=ChartExport.snapshot(true,true,d)
        assertEquals(1,p.beadCount);assertEquals(63,p.emptyCount);assertEquals(1,p.counts()[white])
    }
    @Test fun reopenedReviewedProjectCanExportAfterGridReviewWithoutSaveAgain() {
        val p=ChartProject("test","a".repeat(64),grid,ChartRegion(),ChartRegion(),listOf(LegendEntry(0xFFFFFF,"H2",true)),draft(white),true)
        val restored=ProjectCodec.decode(ProjectCodec.encode(p))
        assertFalse(ChartExport.blockers(restored.mardConfirmed,false,restored.draft).isEmpty())
        assertEquals(64,ChartExport.snapshot(restored.mardConfirmed,true,restored.draft).beadCount)
    }
}
