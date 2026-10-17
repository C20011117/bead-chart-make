package dev.pindou.pattern.pattern

/** Pure geometry shared with export tests: every used color receives a complete legend slot. */
data class ExportLayout(val grid: GridSize, val usedColors: Int) {
    init { require(usedColors in 0..minOf(Mard.palette.colors.size,grid.columns*grid.rows)) }
    val cell=32
    val margin=24
    val ruler=cell
    val gridLeft=margin+ruler
    val gridTop=64+ruler
    val width=maxOf(720,(grid.columns+2)*cell+margin*2)
    val legendColumns=maxOf(1,(width-margin*2)/112)
    val legendRows=(usedColors+legendColumns-1)/legendColumns
    val legendTop=gridTop+grid.rows*cell+ruler+24
    val footerTop=legendTop+legendRows*48+16
    val height=footerTop+80
    val pixelCount=width.toLong()*height
    init { require(pixelCount<=25_000_000) { "导出图过大，请降低网格尺寸" } }
    fun legendX(index: Int): Int { require(index in 0 until usedColors); return margin+(index%legendColumns)*112 }
    fun legendY(index: Int): Int { require(index in 0 until usedColors); return legendTop+(index/legendColumns)*48 }
}

object BeadContrast {
    fun luminance(rgb: Int): Double = .2126*ColorMath.linear((rgb shr 16 and 255)/255.0)+
        .7152*ColorMath.linear((rgb shr 8 and 255)/255.0)+.0722*ColorMath.linear((rgb and 255)/255.0)
    fun textRgb(background: Int): Int = if(luminance(background)<.1791287847) 0xFFFFFF else 0x000000
}
