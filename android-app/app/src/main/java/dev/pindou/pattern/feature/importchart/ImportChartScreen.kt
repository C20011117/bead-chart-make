package dev.pindou.pattern.feature.importchart

import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pindou.pattern.chartimport.*
import dev.pindou.pattern.pattern.*
import dev.pindou.pattern.export.PatternRenderer
import dev.pindou.pattern.feature.convert.UsedColorLegend
import kotlin.math.*

@Composable
fun ImportChartScreen(vm: ImportChartViewModel,onBack: ()->Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::pick) }
    val saver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"),vm::export)
    val exportChart: ()->Unit = {
        if(vm.prepareExport()) saver.launch("复原图纸-${s.grid.columns}x${s.grid.rows}.png")
    }
    var link by remember { mutableStateOf("") };var legendMode by remember { mutableStateOf(false) }
    var sampleIndex by remember { mutableStateOf<Int?>(null) }
    var calibrationOpen by remember { mutableStateOf(false) }
    var reviewOpen by remember { mutableStateOf(false) }
    var locateRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.id) { if(s.id!=null) {calibrationOpen=true;legendMode=false;sampleIndex=null} }
    if(calibrationOpen&&s.bitmap!=null) GridAlignmentDialog(s,vm) {calibrationOpen=false}
    var acceptDialog by remember { mutableStateOf(false) }
    var ocrConsent by remember { mutableStateOf(false) }
    var ocrDialog by remember { mutableStateOf(false) }
    if(ocrDialog) AlertDialog(onDismissRequest={ocrDialog=false},title={Text("启用本地文字识别")},
        text={Text("内置 ML Kit 在设备上处理图像和识别结果，不将这些内容发送给 Google。但 SDK 会发送 API 性能和使用指标，并可能连接 Google 服务器。可以取消并使用手动添加色号。")},
        confirmButton={TextButton(onClick={ocrConsent=true;ocrDialog=false;vm.ocr()}) { Text("了解并启用") }},
        dismissButton={TextButton(onClick={ocrDialog=false}) { Text("改用手动录入") }})
    if(reviewOpen && s.draft!=null && s.reviewBitmap!=null) {
        LaunchedEffect(s.id,s.region,s.selected) { vm.loadCellDetail() }
        DisposableEffect(Unit) { onDispose { vm.clearCellDetail() } }
        CellReviewDialog(s,{reviewOpen=false},vm::select,vm::editCells,{vm.undo()},{vm.undo(true)},{vm.save()},exportChart)
    }
    var code by remember(s.selected,s.draft) { mutableStateOf(s.draft?.let { d -> Mard.palette.colors[d[s.selected].takeIf { it>=0 } ?: d.evidence[s.selected].suggested].code } ?: "") }
    var batch by remember { mutableStateOf(false) }
    if(acceptDialog) AlertDialog(onDismissRequest={acceptDialog=false},title={Text("确认采用剩余建议？")},
        text={Text("这不是自动保证正确。请先对照原图核对；白豆/空格不在此操作中，仍须单独选择。可撤销此批量操作。")},
        confirmButton={TextButton(onClick={vm.acceptNonWhiteSuggestions();acceptDialog=false}) { Text("已核对，采用建议") }},
        dismissButton={TextButton(onClick={acceptDialog=false}) { Text("继续校对") }})
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=onBack,enabled=!s.busy) { Text("返回首页") }
        Text("导入已有拼豆图纸",style=MaterialTheme.typography.headlineSmall)
        Text(s.message,color=MaterialTheme.colorScheme.primary)
        if(s.busy) { LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=vm::cancel) { Text("取消当前处理") } }
        Text("按原网格复原，不是再次把截图像素化。首版支持正视、轴对齐图纸；斜拍请先校正。",style=MaterialTheme.typography.bodySmall)
        Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("01  选择图纸",style=MaterialTheme.typography.titleMedium)
            Button(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!s.busy) { Text("从本地选择图片") }
            OutlinedTextField(link,{link=it.take(10000)},label={Text("分享链接／分享文本")},modifier=Modifier.fillMaxWidth(),enabled=!s.busy)
            Text("此步骤联网提取公开图片；OCR 的 SDK 数据处理另行告知。支持通用网页候选提取，不保证抖音、小红书等所有链接；登录或动态页面请先保存原图。",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={vm.discover(link)},enabled=!s.busy&&link.isNotBlank()) { Text("提取公开图片候选") }
            if(s.urls.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max=160.dp)) {
                itemsIndexed(s.urls) { i,url -> TextButton(onClick={vm.previewUrl(url)},enabled=!s.busy) { Text("预览 ${i+1} · ${url.take(100)}",maxLines=2) } }
            }
            s.downloadPreview?.let { bitmap ->
                Canvas(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat()/bitmap.height)) { drawContext.canvas.nativeCanvas.drawBitmap(bitmap,null,RectF(0f,0f,size.width,size.height),Paint(Paint.FILTER_BITMAP_FLAG)) }
                Button(onClick=vm::useDownloaded,enabled=!s.busy) { Text("确认这张是完整图纸，导入") }
            }
            if(s.projects.isNotEmpty()) {
                Text("已保存工程（可跨重启继续校对）")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=150.dp)) { itemsIndexed(s.projects) { _,p -> TextButton(onClick={vm.load(p.first)},enabled=!s.busy) { Text(p.second) } } }
            }
        } }
        if(s.bitmap!=null) {
            OutlinedTextField(s.name,vm::name,label={Text("工程名称")},singleLine=true,enabled=!s.busy,modifier=Modifier.fillMaxWidth())
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(s.mardConfirmed,vm::mard,enabled=!s.busy);Text("我确认这张图纸使用 MARD 色卡",modifier=Modifier.weight(1f)) }
            Text("非 MARD 色号不能直接视为同色。原图已复制到应用私有目录；退出前请保存工程。",style=MaterialTheme.typography.bodySmall)
            Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("02  校准主体和图例",style=MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!legendMode,{legendMode=false;sampleIndex=null},label={Text("格子主体")},enabled=!s.busy)
                    FilterChip(legendMode,{legendMode=true;sampleIndex=null},label={Text("底部图例")},enabled=!s.busy)
                }
                if(legendMode) {
                    Text(if(sampleIndex!=null) "取色中：点击原图图例的纯色处。" else "仅框选底部色号图例；主体网格在独立同屏审核页中调整。",style=MaterialTheme.typography.bodySmall)
                    RegionCanvas(s,true,sampleIndex,onRegion=vm::legendRegion,onSample={x,y -> sampleIndex?.let {vm.sampleLegend(it,x,y)};sampleIndex=null})
                    RegionNudge(s.legendRegion,!s.busy,vm::legendRegion)
                } else {
                    Text("${s.grid.columns} 列 × ${s.grid.rows} 行 · ${if(s.gridConfirmed) "已人工审核" else "待审核"}")
                    Text("新图导入后先自动找网格；原图、覆盖线、移动和格距按钮在同一屏审核，无需上下滚动。",style=MaterialTheme.typography.bodySmall)
                    Button(onClick={calibrationOpen=true},enabled=!s.busy,modifier=Modifier.fillMaxWidth()) {Text("打开同屏对齐 / 审核")}
                }
            } }
            Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("03  以原图图例色号为准",style=MaterialTheme.typography.titleMedium)
                Text("先框选底部图例，再识别。每行都要核对色号和色块；点色块可回到原图重新取色，错误条目可删除。",style=MaterialTheme.typography.bodySmall)
                Row { TextButton(onClick={if(ocrConsent) vm.ocr() else ocrDialog=true},enabled=!s.busy&&s.mardConfirmed) { Text("本地识别图例") };TextButton(onClick=vm::addLegend,enabled=!s.busy) { Text("手动添加") } }
                if(s.legend.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().height(320.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(s.legend) { i,e -> Column {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(42.dp).background(Color(e.rgb or (0xFF shl 24))).border(1.dp,Color.Gray).clickable(enabled=!s.busy) { legendMode=true;sampleIndex=i })
                            OutlinedTextField(e.code,{vm.legend(i,code=it.take(24))},label={Text("MARD 色号")},singleLine=true,modifier=Modifier.weight(1f),enabled=!s.busy,isError=e.index()==null)
                            TextButton(onClick={vm.deleteLegend(i)},enabled=!s.busy) { Text("删除") }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("#%06X".format(e.rgb),modifier=Modifier.weight(1f))
                            TextButton(onClick={sampleIndex=i;legendMode=true},enabled=!s.busy) { Text("原图取色") }
                            TextButton(onClick={vm.legend(i,confirm=true)},enabled=!s.busy&&e.index()!=null) { Text(if(e.confirmed) "✓ 已核对" else "确认此行") }
                        }
                    } }
                }
                Text("图例 ${s.legend.size} 项，已确认 ${s.legend.count { it.confirmed }} 项。颜色相同但色号不同的格子需要人工分辨。",style=MaterialTheme.typography.bodySmall)
                Button(onClick=vm::recognize,enabled=!s.busy&&ImportGate.canRecognize(s.mardConfirmed,s.gridConfirmed,s.legend),modifier=Modifier.fillMaxWidth()) { Text("按原网格逐格识别") }
            } }
            s.draft?.let { draft ->
                Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("04  对照原图校对 · 待确认 ${draft.pendingCount}",style=MaterialTheme.typography.titleMedium)
                    Text("双指缩放/拖动，点格选择。红框表示待确认；棋盘格为空格。上方原图可同时对照。",style=MaterialTheme.typography.bodySmall)
                    DraftCanvas(draft,s.selected,locateRequest,!s.busy,vm::select)
                    Button(onClick={reviewOpen=true},enabled=!s.busy&&s.reviewBitmap!=null,modifier=Modifier.fillMaxWidth()) { Text("原始小格对照 / 按色号分组校对") }
                    val e=draft.evidence[s.selected]
                    Text("第 ${s.selected/draft.grid.columns+1} 行 / ${s.selected%draft.grid.columns+1} 列 · 建议 ${Mard.palette.colors[e.suggested].code}")
                    Text("${e.reason.ifBlank { "图例匹配" }} · 排序分 ${"%.2f".format(e.score)}（不是正确概率）",style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(code,{code=it.uppercase().take(24)},label={Text("改为 MARD 色号")},singleLine=true,enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                    Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(batch,{batch=it},enabled=!s.busy);Text("批量替换同一识别色（可撤销）",modifier=Modifier.weight(1f)) }
                    Row {
                        TextButton(onClick={val i=Mard.palette.colors.indexOfFirst { it.code.equals(code.trim(),true) };if(i>=0) vm.edit(i,batch)},enabled=!s.busy&&Mard.palette.colors.any { it.code.equals(code.trim(),true) }) { Text("应用色号") }
                        TextButton(onClick={vm.edit(Mard.indexOf("H2"),batch)},enabled=!s.busy) { Text("设为白豆") }
                        TextButton(onClick={vm.edit(EMPTY_CELL,batch)},enabled=!s.busy) { Text("设为空格") }
                    }
                    Row { TextButton(onClick={vm.undo()},enabled=!s.busy&&s.canUndo) { Text("撤销") };TextButton(onClick={vm.undo(true)},enabled=!s.busy&&s.canRedo) { Text("重做") };TextButton(onClick={vm.next();locateRequest++},enabled=!s.busy&&draft.pendingCount>0) { Text("定位待确认") } }
                    TextButton(onClick={acceptDialog=true},enabled=!s.busy&&draft.pendingCount>0) { Text("核对后接受其余色号建议（不含白/空格）") }
                    Text("校对完成后直接点底部“导出 PNG 图纸”，不必先保存工程。",style=MaterialTheme.typography.bodySmall)
                } }
            }
            Text("保存工程仅用于继续编辑，不会在相册或文件中生成图纸。导出 PNG 请使用底部按钮。",style=MaterialTheme.typography.bodySmall)
            s.pattern?.let { pattern ->
                Text("已确认图纸",style=MaterialTheme.typography.titleMedium)
                Canvas(Modifier.fillMaxWidth().aspectRatio((pattern.grid.columns+2f)/(pattern.grid.rows+2f))) { PatternRenderer.drawChart(drawContext.canvas.nativeCanvas,pattern,size.width/(pattern.grid.columns+2)) }
                UsedColorLegend(pattern)

            }
        }
        if(s.busy) { LinearProgressIndicator(progress={s.progress},modifier=Modifier.fillMaxWidth());TextButton(onClick=vm::cancel) { Text("取消当前处理") } }
        Text(s.message,color=MaterialTheme.colorScheme.primary)
        Text("v0.7.1 · 本地 OCR 仍会出错，需人工校对。应用私有工程在卸载后会删除；PNG 不能替代可编辑工程。",style=MaterialTheme.typography.bodySmall)
    }
    if(s.bitmap!=null) {
        HorizontalDivider()
        ImportExportActions(s,{vm.save()},exportChart,vm::recognize)
    }
    }
}

@Composable
private fun GridFields(grid: GridSize,enabled: Boolean,onApply: (GridSize)->Unit) {
    var columns by remember(grid) { mutableStateOf(grid.columns.toString()) };var rows by remember(grid) { mutableStateOf(grid.rows.toString()) }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(columns,{columns=it.filter(Char::isDigit).take(3)},label={Text("列数 8–128")},singleLine=true,enabled=enabled,modifier=Modifier.weight(1f))
        OutlinedTextField(rows,{rows=it.filter(Char::isDigit).take(3)},label={Text("行数 8–128")},singleLine=true,enabled=enabled,modifier=Modifier.weight(1f))
    }
    val c=columns.toIntOrNull();val r=rows.toIntOrNull()
    TextButton(onClick={onApply(GridSize(c!!,r!!))},enabled=enabled&&c!=null&&r!=null&&c in 8..128&&r in 8..128) { Text("应用行列数") }
}

@Composable
private fun RegionNudge(r: ChartRegion,enabled: Boolean,onChange: (ChartRegion)->Unit) {
    var edge by remember { mutableIntStateOf(0) }
    Row {
        for((label,dx,dy) in listOf(Triple("整框←",-.0005f,0f),Triple("整框→",.0005f,0f),Triple("整框↑",0f,-.0005f),Triple("整框↓",0f,.0005f))) {
            TextButton(onClick={val x=dx.coerceIn(-r.left,1f-r.right);val y=dy.coerceIn(-r.top,1f-r.bottom)
                onChange(ChartRegion((r.left+x).coerceIn(0f,1f),(r.top+y).coerceIn(0f,1f),(r.right+x).coerceIn(0f,1f),(r.bottom+y).coerceIn(0f,1f)))},enabled=enabled) { Text(label) }
        }
    }
    Row { listOf("左","上","右","下").forEachIndexed { i,name -> TextButton(onClick={edge=i},enabled=enabled) { Text(if(edge==i) "[$name]" else name) } } }
    Row {
        for(delta in listOf(-.005f,-.0005f,.0005f,.005f)) TextButton(onClick={
            val next=when(edge) {
                0 -> r.copy(left=(r.left+delta).coerceIn(0f,r.right-.001f))
                1 -> r.copy(top=(r.top+delta).coerceIn(0f,r.bottom-.001f))
                2 -> r.copy(right=(r.right+delta).coerceIn(r.left+.001f,1f))
                else -> r.copy(bottom=(r.bottom+delta).coerceIn(r.top+.001f,1f))
            };onChange(next)
        },enabled=enabled) { Text(if(delta>0) "+${delta*100}%" else "${delta*100}%") }
    }
}

@Composable
private fun DraftCanvas(draft: RecognitionDraft,selected: Int,locateRequest: Int,enabled: Boolean,onSelect: (Int)->Unit) {
    val grid=draft.grid
    val pattern=remember(draft) { Pattern(grid,IntArray(grid.columns*grid.rows) { if(draft[it]==PENDING_CELL) draft.evidence[it].suggested else draft[it] }) }
    var zoom by remember(grid) { mutableFloatStateOf(1f) };var pan by remember(grid) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(locateRequest,viewport) {
        if(locateRequest>0 && viewport.width>0 && viewport.height>0) {
            val cell=min(viewport.width.toFloat()/grid.columns,viewport.height.toFloat()/grid.rows)*zoom
            pan=Offset((viewport.width/2f-(selected%grid.columns+.5f)*cell).coerceIn(min(0f,viewport.width-cell*grid.columns),0f),
                (viewport.height/2f-(selected/grid.columns+.5f)*cell).coerceIn(min(0f,viewport.height-cell*grid.rows),0f))
        }
    }
    val select by rememberUpdatedState(onSelect)
    Row { TextButton(onClick={zoom=1f;pan=Offset.Zero}) { Text("适合屏幕") };TextButton(onClick={zoom=(zoom*2).coerceAtMost(32f)}) { Text("放大＋") } }
    Canvas(Modifier.fillMaxWidth().height(360.dp).background(Color.White).onSizeChanged { viewport=it }
        .pointerInput(grid,enabled) { detectTapGestures { p ->
            if(enabled) { val cell=min(size.width.toFloat()/grid.columns,size.height.toFloat()/grid.rows)*zoom
                val x=floor((p.x-pan.x)/cell).toInt();val y=floor((p.y-pan.y)/cell).toInt();if(x in 0 until grid.columns && y in 0 until grid.rows) select(y*grid.columns+x)
            }
        } }.pointerInput(grid,enabled) { detectTransformGestures { center,delta,scale,_ ->
            if(enabled) { val next=(zoom*scale).coerceIn(1f,32f);pan=center-(center-pan)*(next/zoom)+delta;zoom=next
                val cell=min(size.width.toFloat()/grid.columns,size.height.toFloat()/grid.rows)*zoom
                pan=Offset(pan.x.coerceIn(min(0f,size.width-cell*grid.columns),0f),pan.y.coerceIn(min(0f,size.height-cell*grid.rows),0f))
            }
        } }) {
        val cell=min(size.width/grid.columns,size.height/grid.rows)*zoom;val canvas=drawContext.canvas.nativeCanvas
        canvas.save();canvas.clipRect(0f,0f,size.width,size.height);canvas.translate(pan.x,pan.y)
        PatternRenderer.drawGrid(canvas,pattern,cell,cell>=20f)
        val paint=Paint().apply { style=Paint.Style.STROKE;color=0xFFEA4F42.toInt();strokeWidth=max(1f,cell*.04f) }
        val clip=canvas.clipBounds
        for(y in floor(clip.top/cell).toInt().coerceAtLeast(0) until ceil(clip.bottom/cell).toInt().coerceAtMost(grid.rows))
            for(x in floor(clip.left/cell).toInt().coerceAtLeast(0) until ceil(clip.right/cell).toInt().coerceAtMost(grid.columns))
                if(draft[y*grid.columns+x]==PENDING_CELL) canvas.drawRect(x*cell+1,y*cell+1,(x+1)*cell-1,(y+1)*cell-1,paint)
        paint.color=0xFF008CFF.toInt();paint.strokeWidth=3f
        canvas.drawRect((selected%grid.columns)*cell,(selected/grid.columns)*cell,(selected%grid.columns+1)*cell,(selected/grid.columns+1)*cell,paint)
        canvas.restore()
    }
}
