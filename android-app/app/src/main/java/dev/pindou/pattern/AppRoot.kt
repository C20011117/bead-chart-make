package dev.pindou.pattern

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pindou.pattern.feature.convert.*
import dev.pindou.pattern.feature.importchart.*

@Composable
fun AppRoot() {
    var route by rememberSaveable { mutableStateOf("home") }
    val photo: ConvertViewModel=viewModel();val importer: ImportChartViewModel=viewModel()
    val p by photo.state.collectAsStateWithLifecycle();val i by importer.state.collectAsStateWithLifecycle()
    BackHandler(route!="home") {
        if(route=="import"&&i.busy) importer.cancel() else if(route=="photo"&&p.busy) photo.cancel() else route="home"
    }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            when(route) {
                "photo" -> ConvertScreen(photo)
                "import" -> ImportChartScreen(importer) { route="home" }
                else -> Column(Modifier.safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Text("拼豆图纸",style=MaterialTheme.typography.headlineLarge)
                    Text("请选择素材类型，避免把已有图纸再次像素化。")
                    Card { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("原图 → 拼豆图纸",style=MaterialTheme.typography.titleLarge)
                        Text("照片、插画转换；全 MARD 291 色。保持已有细线取样路径。")
                        Button(onClick={route="photo"}) { Text("转换普通图片") }
                    } }
                    Card { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("已有图纸 → 可编辑工程",style=MaterialTheme.typography.titleLarge)
                        Text("本地图片／公开链接候选，校准网格，依据底部图例色号逐格复原。支持空格、校对、保存和重新打开。")
                        Button(onClick={route="import"}) { Text("导入／打开已有图纸") }
                    } }
                    Text("图片识别在本地处理；公开链接导入需联网。启用 OCR 前会说明第三方 SDK 的指标数据处理。v0.7.1",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
