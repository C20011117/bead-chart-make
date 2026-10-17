package dev.pindou.pattern.chartimport

import dev.pindou.pattern.pattern.Pattern

/** Export depends on the reviewed matrix, never on whether a project has been saved. */
object ChartExport {
    fun blockers(mardConfirmed: Boolean,gridConfirmed: Boolean,draft: RecognitionDraft?): List<String> = buildList {
        if(!mardConfirmed) add("确认使用 MARD 色卡")
        if(!gridConfirmed) add("打开同屏对齐页并确认网格")
        if(draft==null) add("点击“按原网格逐格识别”，生成格子矩阵")
        else if(draft.pendingCount>0) add("处理剩余 ${draft.pendingCount} 个待确认格（白豆/空格也须明确选择）")
    }
    fun snapshot(mardConfirmed: Boolean,gridConfirmed: Boolean,draft: RecognitionDraft?): Pattern {
        check(blockers(mardConfirmed,gridConfirmed,draft).isEmpty()) { "尚有未完成的导出检查" }
        return draft!!.confirm()
    }
}
