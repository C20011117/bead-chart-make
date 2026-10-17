package dev.pindou.pattern.chartimport

import dev.pindou.pattern.pattern.*

const val EMPTY_CELL=-1
const val PENDING_CELL=-2

data class ChartRegion(val left: Float=0f,val top: Float=0f,val right: Float=1f,val bottom: Float=1f) {
    init { require(listOf(left,top,right,bottom).all { it.isFinite() && it in 0f..1f });require(right-left>=.0009f && bottom-top>=.0009f) }
    fun pixels(w: Int,h: Int)=CropRect(left*w.toDouble(),top*h.toDouble(),(right-left)*w.toDouble(),(bottom-top)*h.toDouble())
}
data class LegendEntry(val rgb: Int,val code: String,val confirmed: Boolean=false) {
    init { require(rgb in 0..0xFFFFFF && code.length<=24) }
    fun index(): Int?=Mard.palette.colors.indexOfFirst { it.code.equals(code.trim(),true) }.takeIf { it>=0 }
}
/** Score is a review-priority heuristic, NOT a probability. */
data class CellEvidence(val suggested: Int,val rgb: Int,val score: Float,val reason: String) {
    init { require(suggested in Mard.palette.colors.indices);require(rgb in 0..0xFFFFFF);require(score.isFinite() && score in 0f..1f);require(reason.length<=200) }
}
class RecognitionDraft(val grid: GridSize,cells: IntArray,evidence: List<CellEvidence>) {
    private val data=cells.copyOf()
    val evidence=evidence.toList()
    init { require(data.size==grid.columns*grid.rows && evidence.size==data.size);require(data.all { it in PENDING_CELL until Mard.palette.colors.size }) }
    operator fun get(i: Int)=data[i]
    fun snapshot()=data.copyOf()
    val pendingCount get()=data.count { it==PENDING_CELL }
    fun change(indices: IntArray,value: Int): RecognitionDraft {
        require(value in EMPTY_CELL until Mard.palette.colors.size)
        val next=data.copyOf();indices.forEach { require(it in next.indices);next[it]=value }
        return RecognitionDraft(grid,next,evidence)
    }
    fun acceptSuggestions(): RecognitionDraft=RecognitionDraft(grid,IntArray(data.size) { if(data[it]==PENDING_CELL) evidence[it].suggested else data[it] },evidence)
    fun confirm(): Pattern { check(pendingCount==0) { "仍有待确认格" };return Pattern(grid,data) }
}
/** Bounded delta history; bulk edits do not retain full evidence/images. */
class DraftHistory {
    private data class Edit(val indices: IntArray,val before: IntArray,val after: IntArray)
    private val undo=ArrayDeque<Edit>();private val redo=ArrayDeque<Edit>()
    val canUndo get()=undo.isNotEmpty();val canRedo get()=redo.isNotEmpty()
    fun clear() { undo.clear();redo.clear() }
    fun record(old: RecognitionDraft,new: RecognitionDraft) {
        require(old.grid==new.grid)
        val indices=(0 until old.grid.columns*old.grid.rows).filter { old[it]!=new[it] }.toIntArray()
        if(indices.isEmpty()) return
        undo.addLast(Edit(indices,IntArray(indices.size) { old[indices[it]] },IntArray(indices.size) { new[indices[it]] }))
        while(undo.size>40) undo.removeFirst()
        redo.clear()
    }
    private fun apply(d: RecognitionDraft,e: Edit,forward: Boolean): RecognitionDraft {
        val cells=d.snapshot();e.indices.forEachIndexed { j,i -> cells[i]=if(forward) e.after[j] else e.before[j] }
        return RecognitionDraft(d.grid,cells,d.evidence)
    }
    fun undo(d: RecognitionDraft): RecognitionDraft { val e=undo.removeLastOrNull() ?: return d;redo.addLast(e);return apply(d,e,false) }
    fun redo(d: RecognitionDraft): RecognitionDraft { val e=redo.removeLastOrNull() ?: return d;undo.addLast(e);return apply(d,e,true) }
}
