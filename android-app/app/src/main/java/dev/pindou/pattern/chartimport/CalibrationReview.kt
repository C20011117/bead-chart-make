package dev.pindou.pattern.chartimport

import dev.pindou.pattern.pattern.GridSize
import kotlin.math.*

/** Coordinates are in upright source/preview pixels, never screen pixels. */
object Calibration {
    fun fromPixels(x: Float,y: Float,pitchX: Float,pitchY: Float,grid: GridSize,width: Int,height: Int): ChartRegion? {
        if(width<=0 || height<=0 || listOf(x,y,pitchX,pitchY).any { !it.isFinite() } || pitchX<=0 || pitchY<=0) return null
        val right=x+pitchX*grid.columns;val bottom=y+pitchY*grid.rows
        if(x<0 || y<0 || right>width+.001f || bottom>height+.001f) return null
        return runCatching { ChartRegion(x/width,y/height,(right/width).coerceAtMost(1f),(bottom/height).coerceAtMost(1f)) }.getOrNull()
    }
    fun shift(r: ChartRegion,dx: Float,dy: Float): ChartRegion {
        require(dx.isFinite() && dy.isFinite())
        val x=dx.coerceIn(-r.left,1f-r.right);val y=dy.coerceIn(-r.top,1f-r.bottom)
        return ChartRegion((r.left+x).coerceIn(0f,1f),(r.top+y).coerceIn(0f,1f),(r.right+x).coerceIn(0f,1f),(r.bottom+y).coerceIn(0f,1f))
    }
    /** Change pitch while keeping the upper-left grid origin fixed. Reject out-of-image edits. */
    fun pitch(r: ChartRegion,g: GridSize,dx: Float,dy: Float): ChartRegion? = runCatching {
        ChartRegion(r.left,r.top,r.right+g.columns*dx,r.bottom+g.rows*dy)
    }.getOrNull()
}

/** A uniform, centered image transform shared by drawing and hit testing. */
data class ImageViewport(val width: Float,val height: Float,val imageWidth: Int,val imageHeight: Int,
    val zoom: Float=1f,val panX: Float=0f,val panY: Float=0f) {
    init { require(width>0 && height>0 && imageWidth>0 && imageHeight>0 && zoom.isFinite() && zoom>=1f) }
    val scale=min(width/imageWidth,height/imageHeight)*zoom
    val left=(width-imageWidth*scale)/2+panX
    val top=(height-imageHeight*scale)/2+panY
    fun source(x: Float,y: Float)=Pair((x-left)/scale/imageWidth,(y-top)/scale/imageHeight)
    fun screen(x: Float,y: Float)=Pair(left+x*imageWidth*scale,top+y*imageHeight*scale)
    fun bounded(): ImageViewport=copy(panX=panX.coerceIn(-max(0f,(imageWidth*scale-width)/2),max(0f,(imageWidth*scale-width)/2)),
        panY=panY.coerceIn(-max(0f,(imageHeight*scale-height)/2),max(0f,(imageHeight*scale-height)/2)))
    fun transform(centerX: Float,centerY: Float,factor: Float,dx: Float=0f,dy: Float=0f): ImageViewport {
        val next=(zoom*factor).coerceIn(1f,24f);val ratio=next/zoom
        val x=centerX-width/2;val y=centerY-height/2
        return copy(zoom=next,panX=x-(x-panX)*ratio+dx,panY=y-(y-panY)*ratio+dy).bounded()
    }
}

enum class ReviewMode { PENDING, ALL, EMPTY, CODE }
object CellReview {
    /** Code filtering includes explicitly labelled pending suggestions, but never silently accepts them. */
    fun indices(d: RecognitionDraft,mode: ReviewMode,code: Int?=null): List<Int> {
        val indices=d.evidence.indices.filter { i -> when(mode) {
            ReviewMode.PENDING -> d[i]==PENDING_CELL
            ReviewMode.ALL -> true
            ReviewMode.EMPTY -> d[i]==EMPTY_CELL
            ReviewMode.CODE -> code!=null && (d[i]==code || (d[i]==PENDING_CELL && d.evidence[i].suggested==code))
        } }
        return if(mode==ReviewMode.PENDING) indices.sortedWith(compareBy<Int> { d.evidence[it].score }.thenBy { it }) else indices
    }
    fun changeSelected(d: RecognitionDraft,indices: IntArray,value: Int): RecognitionDraft = d.change(indices.distinct().toIntArray(),value)
    fun cellRegion(grid: GridSize,index: Int): ChartRegion {
        require(index in 0 until grid.columns*grid.rows)
        val x=index%grid.columns;val y=index/grid.columns
        return ChartRegion(x.toFloat()/grid.columns,y.toFloat()/grid.rows,(x+1f)/grid.columns,(y+1f)/grid.rows)
    }
}

object ImportGate {
    fun canRecognize(mardConfirmed: Boolean,gridConfirmed: Boolean,legend: List<LegendEntry>): Boolean =
        mardConfirmed && gridConfirmed && legend.isNotEmpty() && legend.all { it.confirmed && it.index()!=null }
    fun canConfirm(mardConfirmed: Boolean,gridConfirmed: Boolean,draft: RecognitionDraft?): Boolean =
        mardConfirmed && gridConfirmed && draft!=null && draft.pendingCount==0
}
