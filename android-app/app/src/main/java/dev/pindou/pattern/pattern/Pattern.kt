package dev.pindou.pattern.pattern

import kotlin.math.*

data class BeadColor(val code: String, val displayRgb: Int)
data class PaletteRevision(val id: String, val colors: List<BeadColor>)
data class GridSize(val columns: Int, val rows: Int) {
    init { require(columns in 8..128 && rows in 8..128 && columns * rows <= 16384) }
}
data class CropSpec(val centerX: Float = .5f, val centerY: Float = .5f, val scale: Float = 1f, val fitImage: Boolean = false) {
    init { require(centerX in 0f..1f && centerY in 0f..1f && scale.isFinite() && scale in 1f..8f) }
}
data class CropRect(val left: Double, val top: Double, val width: Double, val height: Double)
fun cropRect(width: Int, height: Int, grid: GridSize, crop: CropSpec): CropRect {
    val ratio = grid.columns.toDouble() / grid.rows
    if(crop.fitImage) {
        val w=max(width.toDouble(),height*ratio)
        val h=w/ratio
        return CropRect((width-w)/2,(height-h)/2,w,h)
    }
    val w = min(width.toDouble(), height * ratio) / crop.scale
    val h = w / ratio
    return CropRect((crop.centerX * width - w/2).coerceIn(0.0, width-w),
        (crop.centerY * height-h/2).coerceIn(0.0, height-h), w, h)
}
data class ColorUsage(val index: Int, val code: String, val displayRgb: Int, val count: Int)
class Pattern(val grid: GridSize, cells: IntArray, val paletteRevision: String = Mard.palette.id) {
    private val data = cells.copyOf()
    private val totals: IntArray
    init {
        require(data.size == grid.columns * grid.rows)
        require(data.all { it in -1 until Mard.palette.colors.size })
        require(paletteRevision == Mard.palette.id)
        totals = IntArray(Mard.palette.colors.size)
        data.forEach { if(it>=0) totals[it]++ }
    }
    val emptyCount: Int get()=data.count { it==-1 }
    val beadCount: Int get()=data.size-emptyCount
    operator fun get(index: Int): Int = data[index]
    fun counts(): IntArray = totals.copyOf()
    fun snapshot(): IntArray = data.copyOf()
    fun usedColors(): List<ColorUsage> = totals.indices.filter { totals[it]>0 }.map { i ->
        val color=Mard.palette.colors[i]
        ColorUsage(i,color.code,color.displayRgb,totals[i])
    }
}
