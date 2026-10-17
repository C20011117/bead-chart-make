package dev.pindou.pattern.feature.convert

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pindou.pattern.pattern.*
import dev.pindou.pattern.export.PatternRenderer
import android.graphics.Paint
import kotlin.math.*

private val Ink = Color(0xFF20372F)
@Composable
fun ConvertScreen(vm: ConvertViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showPalette by remember { mutableStateOf(false) }
    var fullChart by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::pick) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"),vm::save)
    MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF376C55),secondaryContainer=Color(0xFFDFEBE1),onSecondaryContainer=Ink,background=Color(0xFFF5F4EE),surface=Color.White,onBackground=Ink)) {
        if(showPalette) PaletteDialog { showPalette=false }
        if(fullChart) state.pattern?.let { pattern ->
            androidx.compose.ui.window.Dialog(onDismissRequest={fullChart=false},
                properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
                Surface(Modifier.fillMaxSize(),color=Color.White) {
                    Column(Modifier.safeDrawingPadding().padding(12.dp)) {
                        TextButton(onClick={fullChart=false}) { Text("关闭大图") }
                        PatternPreview(pattern,Modifier.weight(1f))
                    }
                }
            }
        }
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("BEAD / STUDIO",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                Text("把喜欢，拼成一格一格",style=MaterialTheme.typography.headlineMedium,color=Ink)
                Text("本地处理 · MARD 291 色 · 无需联网",style=MaterialTheme.typography.bodyMedium)
                Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("01  选择与裁剪",style=MaterialTheme.typography.titleMedium)
                        Button(onClick={ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },enabled=!state.busy) {
                            Text(if(state.bitmap==null) "选择一张图片" else "更换图片")
                        }
                        state.bitmap?.let { bitmap ->
                            val rect = cropRect(bitmap.width,bitmap.height,state.grid,state.crop)
                            BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                            val ratio=state.grid.columns.toFloat()/state.grid.rows
                            val cropHeight=minOf(maxWidth/ratio,360.dp)
                            Canvas(Modifier.width(cropHeight*ratio).height(cropHeight)
                                .background(Color(Mard.backgroundRgb or (0xFF shl 24))).pointerInput(bitmap,state.grid,state.busy) {
                                    detectTransformGestures { _,pan,zoom,_ ->
                                        val now=vm.state.value
                                        val bounds=cropRect(bitmap.width,bitmap.height,now.grid,now.crop)
                                        if(now.crop.fitImage) return@detectTransformGestures
                                        val newScale=(now.crop.scale*zoom).coerceIn(1f,8f)
                                        val cx=((bounds.left+bounds.width/2-pan.x*bounds.width/size.width)/bitmap.width).toFloat().coerceIn(0f,1f)
                                        val cy=((bounds.top+bounds.height/2-pan.y*bounds.height/size.height)/bitmap.height).toFloat().coerceIn(0f,1f)
                                        vm.crop(CropSpec(cx,cy,newScale))
                                    }
                                }) {
                                val canvas=drawContext.canvas.nativeCanvas
                                canvas.save(); canvas.clipRect(0f,0f,size.width,size.height)
                                // Float source transform keeps preview and sampling crop coordinates identical.
                                canvas.scale((size.width/rect.width).toFloat(),(size.height/rect.height).toFloat())
                                canvas.translate(-rect.left.toFloat(),-rect.top.toFloat())
                                canvas.drawBitmap(bitmap,0f,0f,Paint(Paint.FILTER_BITMAP_FLAG)); canvas.restore()
                                for(i in 1..2) {
                                    drawLine(Color.White.copy(alpha=.6f),Offset(size.width*i/3,0f),Offset(size.width*i/3,size.height))
                                    drawLine(Color.White.copy(alpha=.6f),Offset(0f,size.height*i/3),Offset(size.width,size.height*i/3))
                                }
                            }
                            }
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Switch(checked=state.crop.fitImage,onCheckedChange={vm.crop(CropSpec(fitImage=it))},enabled=!state.busy)
                                Text("完整原图 · 自动补白")
                            }
                            Text("双指缩放、拖动调整裁剪 · ${"%.1f".format(state.crop.scale)}×",style=MaterialTheme.typography.bodySmall)
                            Slider(value=state.crop.scale,onValueChange={vm.crop(state.crop.copy(scale=it))},valueRange=1f..8f,enabled=!state.busy&&!state.crop.fitImage)
                            TextButton(onClick={vm.crop(CropSpec(fitImage=state.crop.fitImage))},enabled=!state.busy) { Text("重置裁剪") }
                        }
                    }
                }
                Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("02  设置网格",style=MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf(32,64,128).forEach { n -> FilterChip(selected=state.grid==GridSize(n,n),onClick={vm.grid(n,n)},enabled=!state.busy,label={Text("$n × $n")}) }
                        }
                        Text("列数  ${state.grid.columns}")
                        Slider(state.grid.columns.toFloat(),{vm.grid(it.roundToInt(),state.grid.rows)},valueRange=8f..128f,steps=119,enabled=!state.busy)
                        Text("行数  ${state.grid.rows}    /    共 ${state.grid.columns*state.grid.rows} 格")
                        Slider(state.grid.rows.toFloat(),{vm.grid(state.grid.columns,it.roundToInt())},valueRange=8f..128f,steps=119,enabled=!state.busy)
                        Text("已导入 MARD ${Mard.palette.colors.size} 个色号，全部参与颜色匹配，不限制用色数量。",style=MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected=state.options.style==SamplingStyle.ILLUSTRATION,
                                onClick={vm.options(state.options.copy(style=SamplingStyle.ILLUSTRATION))},enabled=!state.busy,label={Text("插画模式")})
                            FilterChip(selected=state.options.style==SamplingStyle.PHOTO,
                                onClick={vm.options(state.options.copy(style=SamplingStyle.PHOTO))},enabled=!state.busy,label={Text("照片模式")})
                        }
                        if(state.options.style==SamplingStyle.ILLUSTRATION) {
                            Text("保留原图深浅细线，不强制加黑。低格数无法完整保留所有亚格细节。",style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick={showPalette=true}) { Text("查看完整色卡 / 搜索色号") }
                        Button(onClick=vm::generate,enabled=state.bitmap!=null&&!state.busy,modifier=Modifier.fillMaxWidth()) { Text("生成全彩图纸") }
                        if(state.busy) {
                            Text(state.operation.orEmpty() + if(state.operation=="生成图纸") " · ${(state.progress*100).toInt()}%" else "",style=MaterialTheme.typography.bodySmall)
                            if(state.operation=="生成图纸") LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth())
                            else LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                            TextButton(onClick=vm::cancel) { Text("取消当前操作") }
                        }
                        state.message?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
                    }
                }
                state.pattern?.let { pattern ->
                    Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
                        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("03  你的拼豆图纸",style=MaterialTheme.typography.titleMedium)
                            PatternPreview(pattern)
                            Text("图纸始终绘制色号；双指放大后阅读。可切换纯色预览。",style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={fullChart=true}) { Text("打开全屏图纸") }
                            UsedColorLegend(pattern)
                            Button(onClick={if(vm.prepareExport()) saver.launch("拼豆图纸-${pattern.grid.columns}x${pattern.grid.rows}.png")},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text("保存 PNG 图纸") }
                        }
                    }
                }
                Text("v0.7.1 · ${Mard.palette.id}\nBeadColors 第三方屏显参考，非官方实物测量。扩展色材质与库存请核对店家。结果仅保留于当前会话。",style=MaterialTheme.typography.bodySmall,color=Color(0xFF68756D))
            }
        }
    }
}
@Composable
private fun PatternPreview(pattern: Pattern, modifier: Modifier = Modifier) {
    var zoom by remember(pattern) { mutableFloatStateOf(1f) }
    var pan by remember(pattern) { mutableStateOf(Offset.Zero) }
    var labels by remember(pattern) { mutableStateOf(true) }
    Column(modifier) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={zoom=1f;pan=Offset.Zero}) { Text("适合屏幕") }
            TextButton(onClick={zoom=(zoom*2).coerceAtMost(32f)}) { Text("放大＋") }
            TextButton(onClick={labels=!labels}) { Text(if(labels) "纯色预览" else "色号图纸") }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(minOf(maxWidth,maxHeight)).background(Color.White).pointerInput(pattern) {
            detectTransformGestures { centroid,delta,scale,_ ->
                val next=(zoom*scale).coerceIn(1f,32f)
                pan=centroid-(centroid-pan)*(next/zoom)+delta
                zoom=next
                val cell=min(size.width.toFloat()/(pattern.grid.columns+2),size.height.toFloat()/(pattern.grid.rows+2))*zoom
                pan=Offset(pan.x.coerceIn(min(0f,size.width-cell*(pattern.grid.columns+2)),0f),
                    pan.y.coerceIn(min(0f,size.height-cell*(pattern.grid.rows+2)),0f))
            }
        }) {
            val cell=min(size.width/(pattern.grid.columns+2),size.height/(pattern.grid.rows+2))*zoom
            val canvas=drawContext.canvas.nativeCanvas
            canvas.save(); canvas.clipRect(0f,0f,size.width,size.height); canvas.translate(pan.x,pan.y)
            if(labels) PatternRenderer.drawChart(canvas,pattern,cell)
            else { canvas.translate(cell,cell); PatternRenderer.drawGrid(canvas,pattern,cell,false,false) }
            canvas.restore()
        }
        }
    }
}
