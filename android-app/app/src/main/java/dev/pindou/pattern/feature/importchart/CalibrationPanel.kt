package dev.pindou.pattern.feature.importchart

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.pindou.pattern.chartimport.*
import dev.pindou.pattern.pattern.GridSize
import java.util.Locale

@Composable
internal fun CalibrationFields(r: ChartRegion,g: GridSize,width: Int,height: Int,enabled: Boolean,onChange: (ChartRegion)->Unit) {
    fun format(v: Float)=String.format(Locale.ROOT,"%.6f",v)
    var x by remember(r,g,width,height) { mutableStateOf(format(r.left*width)) }
    var y by remember(r,g,width,height) { mutableStateOf(format(r.top*height)) }
    var px by remember(r,g,width,height) { mutableStateOf(format((r.right-r.left)*width/g.columns)) }
    var py by remember(r,g,width,height) { mutableStateOf(format((r.bottom-r.top)*height/g.rows)) }
    var step by remember { mutableFloatStateOf(.1f) }
    var notice by remember { mutableStateOf("") }
    Text("精调起点 / 格距（转正预览像素，与屏幕缩放无关）",style=MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(x,{x=it.take(14)},label={Text("起点 X")},singleLine=true,enabled=enabled,modifier=Modifier.weight(1f))
        OutlinedTextField(y,{y=it.take(14)},label={Text("起点 Y")},singleLine=true,enabled=enabled,modifier=Modifier.weight(1f))
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(px,{px=it.take(14)},label={Text("横向格距")},singleLine=true,enabled=enabled,modifier=Modifier.weight(1f))
        OutlinedTextField(py,{py=it.take(14)},label={Text("纵向格距")},singleLine=true,enabled=enabled,modifier=Modifier.weight(1f))
    }
    val candidate=Calibration.fromPixels(x.toFloatOrNull() ?: Float.NaN,y.toFloatOrNull() ?: Float.NaN,
        px.toFloatOrNull() ?: Float.NaN,py.toFloatOrNull() ?: Float.NaN,g,width,height)
    TextButton(onClick={candidate?.let(onChange);notice=""},enabled=enabled&&candidate!=null) { Text("应用起点与格距（重新识别）") }
    if(candidate==null) Text("请输入正格距，整个网格必须在原图范围内。",color=MaterialTheme.colorScheme.error)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(.01f,.1f,1f).forEach { v ->
        FilterChip(step==v,{step=v},label={Text("${v}px")},enabled=enabled)
    } }
    Row { listOf(Triple("起点←",-1,0),Triple("起点→",1,0),Triple("起点↑",0,-1),Triple("起点↓",0,1)).forEach { (label,dx,dy) ->
        TextButton(onClick={notice="";onChange(Calibration.shift(r,dx*step/width,dy*step/height))},enabled=enabled) { Text(label) }
    } }
    for(horizontal in listOf(true,false)) Row {
        Text(if(horizontal) "横向格距" else "纵向格距",modifier=Modifier.weight(1f))
        for(sign in listOf(-1,1)) TextButton(onClick={
            val next=Calibration.pitch(r,g,if(horizontal) sign*step/width else 0f,if(horizontal) 0f else sign*step/height)
            if(next==null) notice="格距调整已到边界；请先移动起点或检查行列数。" else {notice="";onChange(next)}
        },enabled=enabled) { Text(if(sign<0) "－" else "＋") }
    }
    Text("移动起点不改变格距；改格距固定左上起点。检查左上、右上和右下格线是否都贴合，勿包含坐标或图例。",style=MaterialTheme.typography.bodySmall)
    if(notice.isNotEmpty()) Text(notice,color=MaterialTheme.colorScheme.error)
}

@Composable
internal fun RegionCanvas(s: ImportChartState,legendMode: Boolean,sampleIndex: Int?,modifier: Modifier=Modifier,compact: Boolean=false,onRegion: (ChartRegion)->Unit,onSample: (Float,Float)->Unit) {
    val bitmap=s.bitmap ?: return
    val region=if(legendMode) s.legendRegion else s.region
    var viewport by remember(bitmap) { mutableStateOf(IntSize.Zero) }
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var pan by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var navigate by remember { mutableStateOf(true) }
    var overlay by remember { mutableStateOf(true) }
    var handle by remember { mutableIntStateOf(-1) }
    val current by rememberUpdatedState(region)
    val change by rememberUpdatedState(onRegion)
    val sample by rememberUpdatedState(onSample)
    fun view()=ImageViewport(viewport.width.coerceAtLeast(1).toFloat(),viewport.height.coerceAtLeast(1).toFloat(),bitmap.width,bitmap.height,zoom,pan.x,pan.y).bounded()
    fun adopt(v: ImageViewport) { zoom=v.zoom;pan=Offset(v.panX,v.panY) }
    fun handles(r: ChartRegion)=listOf(Offset(r.left,r.top),Offset(r.right,r.bottom),Offset(r.left,(r.top+r.bottom)/2),Offset(r.right,(r.top+r.bottom)/2),Offset((r.left+r.right)/2,r.top),Offset((r.left+r.right)/2,r.bottom))
    if(!compact) Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        FilterChip(navigate,{navigate=true},label={Text("移动 / 缩放")},enabled=!s.busy)
        FilterChip(!navigate,{navigate=false},label={Text("拖动框选")},enabled=!s.busy)
    }
    Row {
        if(compact) FilterChip(!navigate,{navigate=!navigate},label={Text("框选")},enabled=!s.busy)
        TextButton(onClick={zoom=1f;pan=Offset.Zero},enabled=!s.busy) { Text("全图") }
        for(factor in listOf(.5f,2f)) TextButton(onClick={val v=view();adopt(v.transform(v.width/2,v.height/2,factor))},enabled=!s.busy) { Text(if(factor<1) "－" else "＋") }
        TextButton(onClick={overlay=!overlay},enabled=!s.busy) { Text(if(overlay) "隐藏线" else "显示线") }
    }
    if(!compact) Text(if(sampleIndex!=null) "原图取色：可先移动放大，再轻点纯色处。" else if(navigate) "双指缩放 / 拖动查看。切到“拖动框选”后，只拖蓝色控制点；不会误移整张网格。" else "拖动蓝色边界点调整范围。移动图片请切换模式；细调请用下方起点 / 格距。",style=MaterialTheme.typography.bodySmall)
    LaunchedEffect(bitmap,viewport,s.autoGrid) {
        if(compact&&viewport.width>0&&viewport.height>0) {
            val v=view();val r=region
            val base=minOf(v.width/bitmap.width,v.height/bitmap.height)
            val z=(minOf(v.width/(bitmap.width*(r.right-r.left)),v.height/(bitmap.height*(r.bottom-r.top)))*.94f/base).coerceIn(1f,24f)
            val scale=base*z
            adopt(ImageViewport(v.width,v.height,bitmap.width,bitmap.height,z,
                (.5f-(r.left+r.right)/2)*bitmap.width*scale,(.5f-(r.top+r.bottom)/2)*bitmap.height*scale).bounded())
        }
    }
    Canvas(modifier.then(if(compact) Modifier else Modifier.height(420.dp)).fillMaxWidth().background(Color(0xFF20252B)).onSizeChanged { viewport=it }
        .pointerInput(bitmap,s.busy,sampleIndex) { detectTapGestures { p ->
            if(!s.busy&&sampleIndex!=null) {val (x,y)=view().source(p.x,p.y);if(x in 0f..1f&&y in 0f..1f) sample(x,y)}
        } }
        .pointerInput(bitmap,navigate,s.busy,sampleIndex,legendMode) {
            if(navigate) detectTransformGestures { center,delta,factor,_ ->
                if(!s.busy) adopt(view().transform(center.x,center.y,factor,delta.x,delta.y))
            } else detectDragGestures(onDragStart={p ->
                val v=view();val points=handles(current).map { val (x,y)=v.screen(it.x,it.y);Offset(x,y) }
                val nearest=points.indices.minBy { (points[it]-p).getDistance() }
                handle=if((points[nearest]-p).getDistance()<=28.dp.toPx()) nearest else -1
            },onDragEnd={handle=-1},onDragCancel={handle=-1}) { event,_ ->
                if(s.busy || sampleIndex!=null || handle<0) return@detectDragGestures
                event.consume();val (sx,sy)=view().source(event.position.x,event.position.y)
                val x=sx.coerceIn(0f,1f);val y=sy.coerceIn(0f,1f);val r=current
                val next=runCatching { when(handle) {
                    0 -> r.copy(left=x.coerceIn(0f,r.right-.0009f),top=y.coerceIn(0f,r.bottom-.0009f))
                    1 -> r.copy(right=x.coerceIn(r.left+.0009f,1f),bottom=y.coerceIn(r.top+.0009f,1f))
                    2 -> r.copy(left=x.coerceIn(0f,r.right-.0009f))
                    3 -> r.copy(right=x.coerceIn(r.left+.0009f,1f))
                    4 -> r.copy(top=y.coerceIn(0f,r.bottom-.0009f))
                    else -> r.copy(bottom=y.coerceIn(r.top+.0009f,1f))
                } }.getOrNull();if(next!=null) change(next)
            }
        }) {
        val v=view();val canvas=drawContext.canvas.nativeCanvas
        canvas.save();canvas.clipRect(0f,0f,size.width,size.height);canvas.translate(v.left,v.top);canvas.scale(v.scale,v.scale)
        canvas.drawBitmap(bitmap,0f,0f,Paint())
        if(overlay) {
            val paint=Paint().apply { color=if(legendMode) 0xFF2878DD.toInt() else 0xCCDD4538.toInt();strokeWidth=1.2f/v.scale }
            val l=region.left*bitmap.width;val t=region.top*bitmap.height;val w=(region.right-region.left)*bitmap.width;val h=(region.bottom-region.top)*bitmap.height
            val cols=if(legendMode) 1 else s.grid.columns;val rows=if(legendMode) 1 else s.grid.rows
            for(x in 0..cols) canvas.drawLine(l+w*x/cols,t,l+w*x/cols,t+h,paint)
            for(y in 0..rows) canvas.drawLine(l,t+h*y/rows,l+w,t+h*y/rows,paint)
        }
        val point=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF2878DD.toInt() }
        handles(region).forEach { canvas.drawCircle(it.x*bitmap.width,it.y*bitmap.height,8.dp.toPx()/v.scale,point) }
        point.color=0xFFEE493F.toInt();canvas.drawCircle(region.left*bitmap.width,region.top*bitmap.height,6.dp.toPx()/v.scale,point)
        canvas.restore()
    }
}
