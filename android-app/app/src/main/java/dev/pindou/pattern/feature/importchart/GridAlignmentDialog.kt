package dev.pindou.pattern.feature.importchart

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pindou.pattern.chartimport.Calibration
import dev.pindou.pattern.pattern.GridSize

/** The picture flexes; all normal alignment controls stay pinned. No outer vertical scroll. */
@Composable
internal fun GridAlignmentDialog(s: ImportChartState,vm: ImportChartViewModel,onDismiss: ()->Unit) {
    val bitmap=s.bitmap ?: return
    var mode by remember { mutableIntStateOf(0) }
    var stepIndex by remember { mutableIntStateOf(1) }
    val step=listOf(.01f,.1f,1f)[stepIndex]
    var dimensions by remember { mutableStateOf(false) }
    var columns by remember(s.grid) { mutableStateOf(s.grid.columns.toString()) }
    var rows by remember(s.grid) { mutableStateOf(s.grid.rows.toString()) }
    var rerunWarning by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    val enabled=!s.busy
    fun dismiss() { if(s.busy) vm.cancel() else onDismiss() }
    Dialog(onDismissRequest=::dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal=10.dp,vertical=4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("网格自动对齐 / 审核",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                    TextButton(onClick=::dismiss) {Text(if(s.busy) "取消检测" else "返回")}
                }
                Text("${s.grid.columns} 列 × ${s.grid.rows} 行 · ${if(s.gridConfirmed) "已审核" else "待人工审核"}",style=MaterialTheme.typography.titleMedium)
                Text(if(s.autoGrid==null) "自动结果不确定时请手动核对；默认行列不是识别结果。" else s.autoGrid.note,style=MaterialTheme.typography.bodySmall,maxLines=2)
                if(s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                RegionCanvas(s,false,null,modifier=Modifier.weight(1f),compact=true,onRegion={vm.geometry(region=it)},onSample={_,_ ->})
                HorizontalDivider()
                Text(if(notice.isEmpty()) "看四角是否都贴线；双指缩放、拖图查看。微调会清除旧草稿。" else notice,style=MaterialTheme.typography.bodySmall,maxLines=2)
                s.autoGrid?.withCoordinateBands?.let {
                    Row {
                        TextButton(onClick={vm.useAutoCandidate(false)},enabled=enabled) {Text("主体候选")}
                        TextButton(onClick={vm.useAutoCandidate(true)},enabled=enabled) {Text("含坐标候选 ${it.grid.columns}×${it.grid.rows}")}
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    listOf("起点","横格距","纵格距").forEachIndexed { i,label ->
                        FilterChip(mode==i,{mode=i},label={Text(label)},enabled=enabled)
                    }
                    TextButton(onClick={stepIndex=(stepIndex+1)%3},enabled=enabled) {Text("${step}px")}
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    if(mode==0) {
                        for((label,dx,dy) in listOf(Triple("←",-1,0),Triple("↑",0,-1),Triple("↓",0,1),Triple("→",1,0)))
                            TextButton(onClick={vm.geometry(region=Calibration.shift(s.region,dx*step/bitmap.width,dy*step/bitmap.height));notice=""},enabled=enabled,modifier=Modifier.weight(1f)) {Text(label)}
                    } else {
                        for(sign in listOf(-1,1)) TextButton(onClick={
                            val next=Calibration.pitch(s.region,s.grid,if(mode==1) sign*step/bitmap.width else 0f,if(mode==2) sign*step/bitmap.height else 0f)
                            if(next!=null) {vm.geometry(region=next);notice=""} else notice="已到图像边界，请先调整起点或检查行列。"
                        },enabled=enabled,modifier=Modifier.weight(1f)) {Text(if(sign<0) "减小格距 −" else "增大格距 ＋")}
                    }
                    TextButton(onClick={dimensions=!dimensions},enabled=enabled) {Text("行列")}
                }
                if(dimensions) Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                    OutlinedTextField(columns,{columns=it.filter(Char::isDigit).take(3)},label={Text("列")},singleLine=true,modifier=Modifier.weight(1f),enabled=enabled)
                    OutlinedTextField(rows,{rows=it.filter(Char::isDigit).take(3)},label={Text("行")},singleLine=true,modifier=Modifier.weight(1f),enabled=enabled)
                    val c=columns.toIntOrNull();val r=rows.toIntOrNull()
                    TextButton(onClick={vm.geometry(grid=GridSize(c!!,r!!));dimensions=false},enabled=enabled&&c!=null&&r!=null&&c in 8..128&&r in 8..128) {Text("应用")}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={if(s.draft!=null) rerunWarning=true else vm.detectGrid()},enabled=enabled,modifier=Modifier.weight(1f)) {Text("重新自动识别")}
                    Button(onClick={vm.confirmGrid(true);onDismiss()},enabled=enabled,modifier=Modifier.weight(1f)) {Text("确认对齐")}
                }
            }
        }
        if(rerunWarning) AlertDialog(onDismissRequest={rerunWarning=false},title={Text("重新识别网格？")},
            text={Text("成功采用新网格后会清除当前识别草稿与未保存的色号修正。已保存工程不会被自动覆盖。")},
            confirmButton={TextButton(onClick={rerunWarning=false;vm.detectGrid()}) {Text("重新识别")}},
            dismissButton={TextButton(onClick={rerunWarning=false}) {Text("取消")}})
    }
}
