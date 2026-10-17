package dev.pindou.pattern.feature.convert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pindou.pattern.pattern.*

@Composable
private fun Swatch(rgb: Int, modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp,Color(0xFF909A93),RoundedCornerShape(4.dp))
        .background(Color(rgb or (0xFF shl 24)),RoundedCornerShape(4.dp)))
}

@Composable
fun UsedColorLegend(pattern: Pattern) {
    val used=remember(pattern) { pattern.usedColors() }
    Text("实际用色 ${used.size} 种 · 合计 ${pattern.beadCount} 颗 · ${pattern.emptyCount} 空格",style=MaterialTheme.typography.titleSmall)
    // Bounded lazy list prevents hundreds of entries from pushing the export button far down.
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=280.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        items(used,key={it.code}) { color ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Swatch(color.displayRgb,Modifier.size(24.dp))
                Text(color.code,modifier=Modifier.weight(1f))
                Text("${color.count} 颗")
            }
        }
    }
    if(used.size>8) Text("上下滑动清单查看其余色号；PNG 包含完整用量清单。",style=MaterialTheme.typography.bodySmall)
}

@Composable
fun PaletteDialog(onDismiss: ()->Unit) {
    var query by remember { mutableStateOf("") }
    val colors=remember(query) {
        val needle=query.trim().removePrefix("#")
        Mard.palette.colors.filter { it.code.contains(needle,true) || "%06X".format(it.displayRgb).contains(needle,true) }
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).fillMaxHeight(.9f),shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("MARD 全色卡 · ${Mard.palette.colors.size} 色",style=MaterialTheme.typography.titleLarge)
                Text("全部色号已参与匹配，不限制用色数量。扩展系列的材质与库存请核对店家。",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("搜索色号 / HEX")})
                Text("显示 ${colors.size} / ${Mard.palette.colors.size} 色",style=MaterialTheme.typography.labelMedium)
                LazyVerticalGrid(columns=GridCells.Adaptive(80.dp),modifier=Modifier.weight(1f),
                    horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(colors,key={it.code}) { color ->
                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Swatch(color.displayRgb,Modifier.fillMaxWidth().height(44.dp))
                            Text(color.code,style=MaterialTheme.typography.labelLarge)
                            Text("#%06X".format(color.displayRgb),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text("来源 BeadColors · 第三方屏显参考，不是官方实物测量值。",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) { Text("关闭色卡") }
            }
        }
    }
}
