package dev.pindou.pattern

import dev.pindou.pattern.chartimport.*
import dev.pindou.pattern.pattern.*
import org.junit.Assert.*
import org.junit.Test

class CalibrationReviewTest {
    private val g=GridSize(8,8)
    private val red=Mard.indexOf("F5")
    private val white=Mard.indexOf("H2")
    private fun draft(): RecognitionDraft {
        val cells=IntArray(64) { red };cells[0]=PENDING_CELL;cells[1]=PENDING_CELL;cells[2]=EMPTY_CELL;cells[3]=white
        val evidence=List(64) { CellEvidence(if(it<4) white else red,0xFFFFFF,if(it==1) .1f else .8f,if(it<2) "白豆/空格" else "") }
        return RecognitionDraft(g,cells,evidence)
    }
    @Test fun numericParametersReproduceRoi() {
        assertEquals(ChartRegion(.1f,.2f,.9f,1f),Calibration.fromPixels(10f,20f,10f,10f,g,100,100))
    }
    @Test fun independentPitchKeepsOriginAndOtherAxis() {
        val r=ChartRegion(.1f,.1f,.5f,.5f);val n=Calibration.pitch(r,g,.01f,0f)!!
        assertEquals(r.left,n.left);assertEquals(r.top,n.top);assertEquals(r.bottom,n.bottom);assertEquals(.58f,n.right,.00001f)
    }
    @Test fun verticalPitchKeepsWidth() {
        val r=ChartRegion(.1f,.1f,.5f,.5f);val n=Calibration.pitch(r,g,0f,.01f)!!
        assertEquals(r.right,n.right);assertEquals(.58f,n.bottom,.00001f)
    }
    @Test fun shiftPreservesBothPitchesAtBoundary() {
        val r=ChartRegion(.1f,.2f,.9f,.8f);val n=Calibration.shift(r,1f,-1f)
        assertEquals(1f,n.right,0f);assertEquals(0f,n.top,0f)
        assertEquals(r.right-r.left,n.right-n.left,.00001f);assertEquals(r.bottom-r.top,n.bottom-n.top,.00001f)
    }
    @Test fun invalidPitchDoesNotSilentlyDistortGrid() {
        assertNull(Calibration.pitch(ChartRegion(),g,.01f,0f));assertNull(Calibration.pitch(ChartRegion(),g,-.2f,0f))
        assertNull(Calibration.fromPixels(0f,0f,20f,10f,g,100,100));assertNull(Calibration.fromPixels(0f,0f,0f,10f,g,100,100))
        assertNull(Calibration.fromPixels(Float.NaN,0f,1f,1f,g,100,100));assertNull(Calibration.fromPixels(0f,0f,Float.POSITIVE_INFINITY,1f,g,100,100))
    }
    @Test fun rectangularGridParametersStayIndependent() {
        val r=Calibration.fromPixels(5f,10f,3f,4f,GridSize(29,64),200,400)!!
        assertEquals(92f/200,r.right,.000001f);assertEquals(266f/400,r.bottom,.000001f)
    }
    @Test fun inverseViewportCoordinatesSurviveZoomAndPan() {
        val v=ImageViewport(600f,400f,300,500,4f,120f,-80f).bounded()
        for(x in listOf(0f,.2f,.8f,1f)) for(y in listOf(0f,.3f,1f)) {
            val screen=v.screen(x,y);val source=v.source(screen.first,screen.second)
            assertEquals(x,source.first,.000001f);assertEquals(y,source.second,.000001f)
        }
    }
    @Test fun zoomKeepsFingerAnchorOnSameImageLocation() {
        val v=ImageViewport(800f,600f,800,600)
        val before=v.source(500f,350f);val after=v.transform(500f,350f,2f).source(500f,350f)
        assertEquals(before.first,after.first,.000001f);assertEquals(before.second,after.second,.000001f)
    }
    @Test fun fitViewportDoesNotAllowImageToBeLost() {
        val v=ImageViewport(600f,400f,300,500,1f,9999f,-9999f).bounded()
        assertEquals(0f,v.panX,0f);assertEquals(0f,v.panY,0f)
        assertEquals(24f,v.transform(300f,200f,100f).zoom,0f)
    }
    @Test fun pendingReviewIsOrderedNotAutoAccepted() {
        val d=draft();assertEquals(listOf(1,0),CellReview.indices(d,ReviewMode.PENDING));assertEquals(2,d.pendingCount)
    }
    @Test fun whiteFilterNeverIncludesEmptyCells() {
        assertEquals(listOf(0,1,3),CellReview.indices(draft(),ReviewMode.CODE,white))
        assertEquals(listOf(2),CellReview.indices(draft(),ReviewMode.EMPTY))
        assertTrue(CellReview.indices(draft(),ReviewMode.CODE,null).isEmpty())
    }
    @Test fun explicitSelectionOnlyChangesSelectedCells() {
        val old=draft();val changed=CellReview.changeSelected(old,intArrayOf(0,0),EMPTY_CELL)
        assertEquals(EMPTY_CELL,changed[0]);assertEquals(PENDING_CELL,changed[1]);assertEquals(white,changed[3]);assertEquals(PENDING_CELL,old[0])
        assertEquals(1,changed.pendingCount)
    }
    @Test fun selectedBatchIsOneUndoRedoAction() {
        val old=draft();val edited=CellReview.changeSelected(old,intArrayOf(0,1),white);val history=DraftHistory()
        history.record(old,edited);assertArrayEquals(old.snapshot(),history.undo(edited).snapshot())
        assertArrayEquals(edited.snapshot(),history.redo(old).snapshot());assertEquals(1,edited.confirm().emptyCount)
    }
    @Test(expected=IllegalArgumentException::class) fun invalidSelectedIndexRejected() { CellReview.changeSelected(draft(),intArrayOf(64),white) }
    @Test(expected=IllegalStateException::class) fun partialSelectionDoesNotBypassExportGate() { CellReview.changeSelected(draft(),intArrayOf(0),white).confirm() }
    @Test fun cellRoisTileAllSupportedSizes() {
        for(n in listOf(8,29,64,128)) {
            val grid=GridSize(n,n)
            val first=CellReview.cellRegion(grid,0);val last=CellReview.cellRegion(grid,n*n-1)
            assertEquals(0f,first.left,0f);assertEquals(1f,last.right,0f);assertEquals(1f,last.bottom,0f)
            for(i in 0 until n-1) assertEquals(CellReview.cellRegion(grid,i).right,CellReview.cellRegion(grid,i+1).left,0f)
        }
    }
    @Test fun unconfirmedGeometryBlocksRecognition() {
        val legend=listOf(LegendEntry(0xBB2244,"F5",true))
        assertFalse(ImportGate.canRecognize(true,false,legend));assertTrue(ImportGate.canRecognize(true,true,legend))
    }
    @Test fun unconfirmedOrInvalidLegendStillBlocksRecognition() {
        assertFalse(ImportGate.canRecognize(true,true,emptyList()))
        assertFalse(ImportGate.canRecognize(true,true,listOf(LegendEntry(0xFFFFFF,"H2",false))))
        assertFalse(ImportGate.canRecognize(true,true,listOf(LegendEntry(0xFFFFFF,"not-mard",true))))
    }
    @Test fun confirmedGridDoesNotBypassPendingGate() {
        assertFalse(ImportGate.canConfirm(true,true,draft()));assertFalse(ImportGate.canConfirm(true,true,null))
    }
    @Test fun confirmationRequiresBrandGeometryAndResolvedMatrix() {
        val d=CellReview.changeSelected(draft(),intArrayOf(0,1),white)
        assertTrue(ImportGate.canConfirm(true,true,d));assertFalse(ImportGate.canConfirm(true,false,d));assertFalse(ImportGate.canConfirm(false,true,d))
    }
    @Test fun calibratedSyntheticSheetsExcludeAxesAndLegendAndPreserveEmpties() {
        // Coloured axes and a separate footer would poison a full-image resample.
        val black=Mard.indexOf("H7")
        val entries=listOf(LegendEntry(0xBB2244,"F5",true),LegendEntry(0x000000,"H7",true),LegendEntry(0xFFFFFF,"H2",true))
        for(n in listOf(8,29,64,128)) {
            val cell=12;val origin=16;val width=n*cell+32;val height=n*cell+80;val grid=GridSize(n,n)
            val source=IntArray(width*height) { 0xFFFF00FF.toInt() }
            val truth=IntArray(n*n) { when(it%4) { 0->red;1->black;2->white;else->EMPTY_CELL } }
            for(y in 0 until n*cell) for(x in 0 until n*cell) {
                val value=truth[(y/cell)*n+x/cell]
                val bg=when(value) { red->0xFFBB2244.toInt();black->0xFF000000.toInt();else->-1 }
                source[(origin+y)*width+origin+x]=when {
                    x%cell==0||y%cell==0 -> 0xFF888888.toInt()
                    value!=EMPTY_CELL&&x%cell in 5..6&&y%cell in 5..6 -> if(value==black) -1 else 0xFF000000.toInt()
                    else -> bg
                }
            }
            val roi=Calibration.fromPixels(origin.toFloat(),origin.toFloat(),cell.toFloat(),cell.toFloat(),grid,width,height)!!
            assertEquals((origin+n*cell).toFloat()/width,roi.right,.000001f)
            val work=IntArray(n*n*cell*cell) { val x=it%(n*cell);val y=it/(n*cell);source[(origin+y)*width+origin+x] }
            val d=CellRecognizer.recognize(work,n*cell,n*cell,grid,entries)
            for(i in truth.indices) assertEquals("size=$n cell=$i",if(truth[i]==EMPTY_CELL) white else truth[i],d.evidence[i].suggested)
            // Human-reviewed masks distinguish genuine empties from white beads, not a colour guess.
            var corrected=d
            for(value in listOf(red,black,white,EMPTY_CELL)) corrected=CellReview.changeSelected(corrected,truth.indices.filter { truth[it]==value }.toIntArray(),value)
            assertTrue(ImportGate.canConfirm(true,true,corrected))
            val pattern=corrected.confirm();assertArrayEquals(truth,corrected.snapshot())
            assertEquals(n*n,pattern.beadCount+pattern.emptyCount)
        }
    }
}
