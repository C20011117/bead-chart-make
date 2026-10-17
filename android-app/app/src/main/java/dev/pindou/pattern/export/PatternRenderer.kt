package dev.pindou.pattern.export

import android.content.ContentResolver
import android.graphics.*
import android.net.Uri
import dev.pindou.pattern.pattern.*
import kotlinx.coroutines.*
import kotlin.math.*

object PatternRenderer {
    fun drawGrid(canvas: Canvas, pattern: Pattern, cell: Float, labels: Boolean, gridLines: Boolean = true) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val cols=pattern.grid.columns; val rows=pattern.grid.rows
        // Cull off-screen cells, especially when a 128x128 chart is magnified.
        val clip=canvas.clipBounds
        val x0=floor(clip.left/cell).toInt().coerceIn(0,cols)
        val x1=ceil(clip.right/cell).toInt().coerceIn(0,cols)
        val y0=floor(clip.top/cell).toInt().coerceIn(0,rows)
        val y1=ceil(clip.bottom/cell).toInt().coerceIn(0,rows)
        for(y in y0 until y1) for(x in x0 until x1) {
            val index=pattern[y*cols+x]
            if(index==-1) {
                paint.style=Paint.Style.FILL;paint.isAntiAlias=false;paint.color=0xFFF8F8F8.toInt()
                canvas.drawRect(x*cell,y*cell,(x+1)*cell,(y+1)*cell,paint)
                paint.color=0xFFE4E7E4.toInt()
                canvas.drawRect(x*cell,y*cell,(x+.5f)*cell,(y+.5f)*cell,paint)
                canvas.drawRect((x+.5f)*cell,(y+.5f)*cell,(x+1)*cell,(y+1)*cell,paint)
                continue
            }
            val color=Mard.palette.colors[index]
            paint.isAntiAlias=false
            paint.color=color.displayRgb or (0xFF shl 24); paint.style=Paint.Style.FILL
            canvas.drawRect(x*cell,y*cell,(x+1)*cell,(y+1)*cell,paint)
            if(labels) {
                paint.isAntiAlias=true
                paint.color=BeadContrast.textRgb(color.displayRgb) or (0xFF shl 24)
                paint.textSize=cell*.34f; paint.textAlign=Paint.Align.CENTER
                val measured=paint.measureText(color.code)
                if(measured>cell*.88f) paint.textSize*=cell*.88f/measured
                canvas.drawText(color.code,(x+.5f)*cell,(y+.5f)*cell-(paint.ascent()+paint.descent())/2,paint)
            }
        }
        if(gridLines) {
            paint.color=0x80909090.toInt(); paint.strokeWidth=maxOf(.5f,cell/48)
            paint.isAntiAlias=false
            for(x in x0..x1) {
                paint.strokeWidth=if(x%5==0) maxOf(.7f,cell/24) else maxOf(.35f,cell/64)
                canvas.drawLine(x*cell,y0*cell,x*cell,y1*cell,paint)
            }
            for(y in y0..y1) {
                paint.strokeWidth=if(y%5==0) maxOf(.7f,cell/24) else maxOf(.35f,cell/64)
                canvas.drawLine(x0*cell,y*cell,x1*cell,y*cell,paint)
            }
        }
    }

    /** Origin is the outer ruler corner; all coordinates are one-based. */
    fun drawChart(canvas: Canvas, pattern: Pattern, cell: Float, labels: Boolean = true) {
        val cols=pattern.grid.columns; val rows=pattern.grid.rows
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.BLACK; textSize=cell*.32f; textAlign=Paint.Align.CENTER
        }
        val baseline=-(paint.ascent()+paint.descent())/2
        for(x in 0 until cols) {
            canvas.drawText("${x+1}",(x+1.5f)*cell,.5f*cell+baseline,paint)
            canvas.drawText("${x+1}",(x+1.5f)*cell,(rows+1.5f)*cell+baseline,paint)
        }
        for(y in 0 until rows) {
            canvas.drawText("${y+1}",.5f*cell,(y+1.5f)*cell+baseline,paint)
            canvas.drawText("${y+1}",(cols+1.5f)*cell,(y+1.5f)*cell+baseline,paint)
        }
        canvas.save()
        try { canvas.translate(cell,cell); drawGrid(canvas,pattern,cell,labels) }
        finally { canvas.restore() }
    }

    fun drawPage(canvas: Canvas, pattern: Pattern) {
        val used=pattern.usedColors()
        val layout=ExportLayout(pattern.grid,used.size)
        canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK; textSize=22f }
        canvas.drawText("拼豆图纸  ${pattern.grid.columns} × ${pattern.grid.rows}  ·  ${used.size} 种用色",24f,34f,paint)
        canvas.save()
        try {
            canvas.translate(layout.margin.toFloat(),(layout.gridTop-layout.ruler).toFloat())
            drawChart(canvas,pattern,layout.cell.toFloat(),true)
        } finally { canvas.restore() }
        used.forEachIndexed { i,color ->
            val x=layout.legendX(i).toFloat(); val y=layout.legendY(i).toFloat()
            paint.style=Paint.Style.FILL; paint.color=color.displayRgb or (0xFF shl 24)
            canvas.drawRoundRect(x,y,x+104,y+40,5f,5f,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=.6f; paint.color=Color.GRAY
            canvas.drawRoundRect(x,y,x+104,y+40,5f,5f,paint)
            paint.style=Paint.Style.FILL
            paint.color=BeadContrast.textRgb(color.displayRgb) or (0xFF shl 24)
            paint.textAlign=Paint.Align.CENTER; paint.textSize=15f
            canvas.drawText("${color.code} · ${color.count}",x+52,y+25,paint)

        }
        paint.textAlign=Paint.Align.LEFT; paint.textSize=14f
        canvas.drawText("合计 ${pattern.beadCount} 颗 · ${pattern.emptyCount} 空格 · 色库 ${Mard.palette.colors.size} 色",24f,layout.footerTop+12f,paint)
        canvas.drawText(pattern.paletteRevision,24f,layout.footerTop+34f,paint)
        canvas.drawText("BeadColors 第三方屏显参考；购买前核对色号与实物材质",24f,layout.footerTop+56f,paint)
    }

    suspend fun save(resolver: ContentResolver, uri: Uri, pattern: Pattern) = withContext(Dispatchers.IO) {
        val layout=ExportLayout(pattern.grid,pattern.usedColors().size)
        val bitmap=Bitmap.createBitmap(layout.width,layout.height,Bitmap.Config.ARGB_8888)
        try {
            ensureActive()
            drawPage(Canvas(bitmap),pattern)
            ensureActive()
            val output=resolver.openOutputStream(uri,"wt") ?: error("无法打开保存位置")
            output.use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) { "PNG 写入失败" } }
            ensureActive()
        } finally { bitmap.recycle() }
    }
}
