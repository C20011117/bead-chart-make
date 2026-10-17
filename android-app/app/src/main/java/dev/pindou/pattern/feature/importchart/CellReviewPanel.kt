package dev.pindou.pattern.feature.importchart

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pindou.pattern.chartimport.*
import dev.pindou.pattern.pattern.*
import kotlin.math.*

@Composable
internal fun RawCell(bitmap: Bitmap,grid: GridSize,index: Int,modifier: Modifier=Modifier,context: Boolean=false,fullImage: Boolean=false) {
    Canvas(modifier.background(Color.White)) {
        val r=CellReview.cellRegion(grid,index)
        val x=index%grid.columns;val y=index/grid.columns
        val left=if(context) max(0,x-1).toFloat()/grid.columns else r.left
        val top=if(context) max(0,y-1).toFloat()/grid.rows else r.top
        val right=if(context) min(grid.columns,x+2).toFloat()/grid.columns else r.right
        val bottom=if(context) min(grid.rows,y+2).toFloat()/grid.rows else r.bottom
        val src=if(fullImage) Rect(0,0,bitmap.width,bitmap.height) else Rect(floor(left*bitmap.width).toInt(),floor(top*bitmap.height).toInt(),ceil(right*bitmap.width).toInt().coerceAtMost(bitmap.width),ceil(bottom*bitmap.height).toInt().coerceAtMost(bitmap.height))
        val scale=min(size.width/src.width(),size.height/src.height())
        val w=src.width()*scale;val h=src.height()*scale;val l=(size.width-w)/2;val t=(size.height-h)/2
        val canvas=drawContext.canvas.nativeCanvas
        canvas.drawBitmap(bitmap,src,RectF(l,t,l+w,t+h),Paint())
        if(context) {
            val paint=Paint().apply { style=Paint.Style.STROKE;color=0xFF008CFF.toInt();strokeWidth=3f }
            canvas.drawRect(l+(r.left*bitmap.width-src.left)*scale,t+(r.top*bitmap.height-src.top)*scale,
                l+(r.right*bitmap.width-src.left)*scale,t+(r.bottom*bitmap.height-src.top)*scale,paint)
        }
    }
}

@Composable
internal fun CellReviewDialog(s: ImportChartState,onDismiss: ()->Unit,onSelect: (Int)->Unit,onApply: (IntArray,Int)->Unit,onUndo: ()->Unit,onRedo: ()->Unit,onSave: ()->Unit,onExport: ()->Unit) {
    val draft=s.draft ?: return
    val bitmap=s.reviewBitmap ?: return
    var mode by remember { mutableStateOf(ReviewMode.PENDING) }
    var filterCode by remember { mutableStateOf("") }
    var targetCode by remember { mutableStateOf("") }
    val palette=Mard.palette.colors
    val codeIndex=palette.indexOfFirst { it.code.equals(filterCode.trim(),true) }.takeIf { it>=0 }
    val indices=remember(draft,mode,codeIndex) { CellReview.indices(draft,mode,codeIndex) }
    var page by remember(mode,filterCode) { mutableIntStateOf(0) }
    val pageCount=max(1,(indices.size+23)/24);val actualPage=page.coerceIn(0,pageCount-1)
    val shown=indices.drop(actualPage*24).take(24)
    var selected by remember(draft,mode,filterCode) { mutableStateOf(setOf<Int>()) }
    var confirm by remember(draft) { mutableStateOf<Int?>(null) }
    fun label(i: Int): String=when(val value=draft[i]) { EMPTY_CELL -> "空格";PENDING_CELL -> "待：${palette[draft.evidence[i].suggested].code}";else -> palette[value].code }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row {
                    Text("原始小格对照校对",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    TextButton(onClick=onDismiss) { Text("返回") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("第 ${s.selected/draft.grid.columns+1} 行 / ${s.selected%draft.grid.columns+1} 列 · ${label(s.selected)}")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            val detail=s.detailBitmap?.takeIf { s.detailIndex==s.selected }
                            Text(if(detail!=null) "原文件小格（≤512px）" else "工作图小格")
                            RawCell(detail ?: bitmap,draft.grid,s.selected,Modifier.fillMaxWidth().height(135.dp),fullImage=detail!=null)
                        }
                        Column(Modifier.weight(1f)) { Text("原图邻格 / 蓝框定位");RawCell(bitmap,draft.grid,s.selected,Modifier.fillMaxWidth().height(135.dp),true) }
                    }
                    if(s.detailMessage.isNotEmpty()) Text(s.detailMessage,style=MaterialTheme.typography.bodySmall)
                    Text(draft.evidence[s.selected].reason.ifBlank { "图例匹配；仍请核对原格色号。" },style=MaterialTheme.typography.bodySmall)
                    Text("下方图片保留原格文字与边框。点小格选中并定位，再点取消；只修改明确选中的格子。放大不会补回原图缺失的细节。",style=MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(ReviewMode.PENDING to "待确认",ReviewMode.ALL to "全部",ReviewMode.EMPTY to "空格",ReviewMode.CODE to "按色号").forEach { (v,name) ->
                            FilterChip(mode==v,{mode=v},label={Text(name)},enabled=!s.busy)
                        }
                    }
                    if(mode==ReviewMode.CODE) {
                        OutlinedTextField(filterCode,{filterCode=it.uppercase().take(24)},label={Text("筛选色号，例如 H2")},singleLine=true,modifier=Modifier.fillMaxWidth(),isError=filterCode.isNotBlank()&&codeIndex==null)
                        Text("包含该色号和该色号的待确认建议；“待”字不代表已经认定正确。",style=MaterialTheme.typography.bodySmall)
                    }
                    Text("共 ${indices.size} 格 · 已选 ${selected.size} 格 · 待确认总数 ${draft.pendingCount}")
                    Row {
                        TextButton(onClick={selected=selected+shown},enabled=!s.busy&&shown.isNotEmpty()) { Text("选中本页") }
                        TextButton(onClick={selected=emptySet()},enabled=selected.isNotEmpty()) { Text("清空选择") }
                    }
                    for(row in shown.chunked(4)) Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        for(i in row) Column(Modifier.weight(1f).border(if(i in selected) 3.dp else 1.dp,if(i in selected) Color(0xFF008CFF) else Color.Gray)
                            .clickable(enabled=!s.busy) { selected=if(i in selected) selected-i else selected+i;onSelect(i) }.padding(3.dp)) {
                            RawCell(bitmap,draft.grid,i,Modifier.fillMaxWidth().height(62.dp))
                            Text("${i/draft.grid.columns+1}:${i%draft.grid.columns+1}",style=MaterialTheme.typography.labelSmall)
                            Text(label(i),style=MaterialTheme.typography.labelSmall,maxLines=1)
                        }
                        repeat(4-row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    if(indices.isEmpty()) Text("当前筛选没有格子；可切换到全部或其他色号。")
                    Row {
                        TextButton(onClick={page=actualPage-1},enabled=actualPage>0) { Text("上一页") }
                        Text("${actualPage+1} / $pageCount",modifier=Modifier.weight(1f))
                        TextButton(onClick={page=actualPage+1},enabled=actualPage<pageCount-1) { Text("下一页") }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(targetCode,{targetCode=it.uppercase().take(24)},label={Text("将选中 ${selected.size} 格改为 MARD 色号")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!s.busy)
                Row {
                    val target=palette.indexOfFirst { it.code.equals(targetCode.trim(),true) }
                    Button(onClick={confirm=target},enabled=!s.busy&&selected.isNotEmpty()&&target>=0,modifier=Modifier.weight(1f)) { Text("应用色号") }
                    TextButton(onClick={confirm=Mard.indexOf("H2")},enabled=!s.busy&&selected.isNotEmpty()) { Text("H2 白豆") }
                    TextButton(onClick={confirm=EMPTY_CELL},enabled=!s.busy&&selected.isNotEmpty()) { Text("空格") }
                }
                Row {
                    TextButton(onClick=onUndo,enabled=!s.busy&&s.canUndo) { Text("撤销") }
                    TextButton(onClick=onRedo,enabled=!s.busy&&s.canRedo) { Text("重做") }
                    Text("校对完成可直接在此导出；有待确认不能导出。",style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
                }
                ImportExportActions(s,onSave,onExport,{})
            }
        }
        confirm?.let { value ->
            AlertDialog(onDismissRequest={confirm=null},title={Text("确认修改 ${selected.size} 格？")},
                text={Text(if(value==EMPTY_CELL) "选中的格子将设为空格，不印色号、不计豆数。请确认不是白豆。" else "将选中的格子明确设为 ${palette[value].code}。包含白色区域时请确认确实有豆子。未选中的格子不会改变；可撤销。")},
                confirmButton={TextButton(onClick={val ids=selected.toIntArray();confirm=null;onApply(ids,value)}) { Text("已核对，修改") }},
                dismissButton={TextButton(onClick={confirm=null}) { Text("取消") }})
        }
    }
}
