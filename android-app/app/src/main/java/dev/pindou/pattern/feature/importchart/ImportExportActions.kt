package dev.pindou.pattern.feature.importchart

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pindou.pattern.chartimport.*

@Composable
internal fun ImportExportActions(s: ImportChartState,onSave: ()->Unit,onExport: ()->Unit,onRecognize: ()->Unit) {
    val blockers=ChartExport.blockers(s.mardConfirmed,s.gridConfirmed,s.draft)
    Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=onSave,enabled=!s.busy,modifier=Modifier.weight(1f)) { Text("保存工程") }
            Button(onClick=onExport,enabled=!s.busy&&blockers.isEmpty(),modifier=Modifier.weight(1f)) { Text("导出 PNG 图纸") }
        }
        if(s.pattern!=null&&s.message.isNotBlank()) Text(s.message,style=MaterialTheme.typography.labelSmall,maxLines=2)
        if(blockers.isEmpty()) Text("点击导出即确认当前校对结果，并选择 PNG 保存位置。工程另存，用于下次续改。",style=MaterialTheme.typography.bodySmall)
        else Row(verticalAlignment=Alignment.CenterVertically) {
            Text("导出前还需：${blockers.joinToString("；")}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
            if(s.draft==null&&ImportGate.canRecognize(s.mardConfirmed,s.gridConfirmed,s.legend))
                TextButton(onClick=onRecognize,enabled=!s.busy) { Text("开始识别") }
        }
    }
}
