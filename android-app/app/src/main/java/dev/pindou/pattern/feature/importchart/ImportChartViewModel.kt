package dev.pindou.pattern.feature.importchart

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pindou.pattern.chartimport.*
import dev.pindou.pattern.image.*
import dev.pindou.pattern.pattern.*
import dev.pindou.pattern.project.*
import dev.pindou.pattern.linkimport.*
import dev.pindou.pattern.export.PatternRenderer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File


data class ImportChartState(val bitmap: Bitmap?=null,val id: String?=null,val hash: String="",val name: String="导入的拼豆图纸",
    val grid: GridSize=GridSize(64,64),val region: ChartRegion=ChartRegion(.03f,.03f,.97f,.87f),
    val legendRegion: ChartRegion=ChartRegion(.03f,.88f,.97f,.98f),val legend: List<LegendEntry> = emptyList(),
    val mardConfirmed: Boolean=false,val draft: RecognitionDraft?=null,val pattern: Pattern?=null,val selected: Int=0,
    val suggestion: GridSuggestion?=null,val projects: List<Pair<String,String>> = emptyList(),
    val urls: List<String> = emptyList(),val downloaded: File?=null,val downloadPreview: Bitmap?=null,
    val busy: Boolean=false,val progress: Float=0f,val message: String="",val canUndo: Boolean=false,val canRedo: Boolean=false,
    val reviewBitmap: Bitmap?=null,val detailBitmap: Bitmap?=null,val detailIndex: Int=-1,val detailMessage: String="",val gridConfirmed: Boolean=false,val autoGrid: AutoGridResult?=null)
class ImportChartViewModel(app: Application): AndroidViewModel(app) {
    private val mutable=MutableStateFlow(ImportChartState());val state=mutable.asStateFlow()
    private val store=PatternProjectStore(app);private val history=DraftHistory();private val fetcher=PublicImageFetcher()
    private var detailTask: Job?=null
    private var task: Job?=null;private var exportSnapshot: Pattern?=null
    private val resolver get()=getApplication<Application>().contentResolver
    init { viewModelScope.launch { try { val items=withContext(Dispatchers.IO) { store.list() };mutable.update { it.copy(projects=items) } } catch(_: Exception) {} } }
    private fun work(message: String,block: suspend ()->Unit) {
        if(state.value.busy) return
        mutable.update { it.copy(busy=true,progress=0f,message=message) }
        task=viewModelScope.launch {
            try { block() }
            catch(e: CancellationException) { mutable.update { it.copy(message="已取消；不会发布未完成识别。已选的 PNG 目标文件请检查是否完整。") };throw e }
            catch(_: OutOfMemoryError) { mutable.update { it.copy(message="内存不足，请裁小原图后重试") } }
            catch(e: Exception) { mutable.update { it.copy(message=e.message?.take(180) ?: "处理失败，请重试") } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    fun cancel() { task?.cancel() }
    fun pick(uri: Uri)=work("复制原图到私有目录…") {
        val previousDownload=state.value.downloaded
        val context=currentCoroutineContext()
        val id=withContext(Dispatchers.IO) { store.import(uri) { context.ensureActive() } }
        try {
            val bitmap=ImagePipeline.decode(resolver,Uri.fromFile(store.source(id)))
            val hash=withContext(Dispatchers.IO) { store.hash(id) }
            mutable.update { it.copy(message="正在自动识别主体、行列与格线起点…") }
            val auto=try { analyzeFullGrid(id) } catch(e: CancellationException) {throw e} catch(_: Exception) {null}
            context.ensureActive();history.clear()
            mutable.update { ImportChartState(bitmap=bitmap,id=id,hash=hash,projects=it.projects,busy=true,
                grid=auto?.primary?.grid ?: GridSize(64,64),region=auto?.primary?.relativeRegion ?: ChartRegion(.03f,.03f,.97f,.87f),
                autoGrid=auto,suggestion=auto?.primary,message=autoMessage(auto)) }
        } catch(t: Throwable) { withContext(NonCancellable+Dispatchers.IO) { store.folder(id).deleteRecursively() };throw t }
        finally { if(previousDownload!=null && uri==Uri.fromFile(previousDownload)) previousDownload.delete() }
    }
    fun name(value: String) { if(!state.value.busy) mutable.update { it.copy(name=value.take(100)) } }
    fun mard(value: Boolean) { if(!state.value.busy) { history.clear();mutable.update { it.copy(mardConfirmed=value,draft=null,pattern=null,reviewBitmap=null,detailBitmap=null,canUndo=false,canRedo=false) } } }
    fun confirmGrid(value: Boolean) {
        if(!state.value.busy) mutable.update { it.copy(gridConfirmed=value,pattern=if(value) it.pattern else null) }
    }
    fun geometry(region: ChartRegion=state.value.region,grid: GridSize=state.value.grid) {
        if(state.value.busy) return
        history.clear();mutable.update { it.copy(region=region,grid=grid,gridConfirmed=false,draft=null,pattern=null,reviewBitmap=null,detailBitmap=null,suggestion=null,canUndo=false,canRedo=false) }
    }
    fun legendRegion(region: ChartRegion) {
        if(state.value.busy) return
        history.clear();mutable.update { it.copy(legendRegion=region,legend=emptyList(),draft=null,pattern=null,reviewBitmap=null,detailBitmap=null,canUndo=false,canRedo=false) }
    }
    private fun setLegend(entries: List<LegendEntry>) {
        if(state.value.busy) return
        history.clear();mutable.update { it.copy(legend=entries,draft=null,pattern=null,reviewBitmap=null,detailBitmap=null,canUndo=false,canRedo=false) }
    }
    fun addLegend() { if(state.value.legend.size<500) setLegend(state.value.legend+LegendEntry(0xFFFFFF,"",false)) }
    fun legend(index: Int,code: String?=null,rgb: Int?=null,confirm: Boolean=false) {
        val list=state.value.legend.toMutableList();if(index !in list.indices) return
        val e=list[index].copy(code=code ?: list[index].code,rgb=rgb ?: list[index].rgb,confirmed=false)
        list[index]=e.copy(confirmed=confirm && e.index()!=null);setLegend(list)
    }
    fun deleteLegend(index: Int) { setLegend(state.value.legend.filterIndexed { i,_ -> i!=index }) }
    fun sampleLegend(index: Int,x: Float,y: Float) {
        val b=state.value.bitmap ?: return
        val pixels=IntArray(b.width*b.height);b.getPixels(pixels,0,b.width,0,0,b.width,b.height)
        val sx=(x*b.width).toInt().coerceIn(0,b.width-1);val sy=(y*b.height).toInt().coerceIn(0,b.height-1)
        val sample=CellRecognizer.background(pixels,b.width,b.height,CropRect((sx-3).coerceAtLeast(0).toDouble(),(sy-3).coerceAtLeast(0).toDouble(),7.0,7.0))
        legend(index,rgb=sample.rgb)
    }
    fun ocr() {
        val s=state.value;val id=s.id ?: return
        if(!s.mardConfirmed) { mutable.update { it.copy(message="请先确认图纸使用 MARD 色卡；其他品牌不能直接按同名色号导入。") };return }
        work("离线识别图例色号；结束后逐项核对颜色与编号…") {
            val context=currentCoroutineContext()
            val bitmap=withContext(Dispatchers.IO) { ChartImageDecoder.decode(store.source(id),s.legendRegion) { context.ensureActive() } }
            val entries=try { LegendOcr.read(getApplication<Application>(),bitmap) } finally { bitmap.recycle() }
            context.ensureActive();history.clear()
            mutable.update { it.copy(legend=entries,draft=null,pattern=null,reviewBitmap=null,detailBitmap=null,canUndo=false,canRedo=false,message=if(entries.isEmpty()) "没有读到 MARD 色号，可重新框选或手动添加。" else "识别到 ${entries.size} 个色号。OCR 可能出错；若取错色块，点取色后在原图图例的纯色位置点一下，再逐项确认。") }
        }
    }
    private fun autoMessage(result: AutoGridResult?): String = if(result==null)
        "未找到可靠完整网格；保留手动候选，请在同屏校准页修正。不会把默认行列当作识别成功。"
        else "自动检测 ${result.primary.grid.columns} 列 × ${result.primary.grid.rows} 行，已叠加对齐，等待人工审核。${result.note}"
    private suspend fun analyzeFullGrid(id: String): AutoGridResult? {
        val ctx=currentCoroutineContext()
        return withContext(Dispatchers.Default) {
            val bitmap=ChartImageDecoder.decode(store.source(id),ChartRegion()) {ctx.ensureActive()}
            try {
                val p=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(p,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                AutoGridDetector.detect(p,bitmap.width,bitmap.height) {ctx.ensureActive()}
            } finally {bitmap.recycle()}
        }
    }
    fun detectGrid() {
        val id=state.value.id ?: return
        work("自动识别整张原图的主体、行列和格线…") {
            val auto=analyzeFullGrid(id);currentCoroutineContext().ensureActive()
            if(auto==null) mutable.update {it.copy(autoGrid=null,suggestion=null,message=autoMessage(null))}
            else {
                history.clear();mutable.update {it.copy(grid=auto.primary.grid,region=auto.primary.relativeRegion,
                    autoGrid=auto,suggestion=auto.primary,gridConfirmed=false,draft=null,pattern=null,reviewBitmap=null,detailBitmap=null,
                    canUndo=false,canRedo=false,message=autoMessage(auto))}
            }
        }
    }
    fun useAutoCandidate(includeCoordinates: Boolean=false) {
        val auto=state.value.autoGrid ?: return
        val candidate=if(includeCoordinates) auto.withCoordinateBands ?: return else auto.primary
        geometry(candidate.relativeRegion,candidate.grid)
    }
    fun recognize() {
        val s=state.value;val id=s.id ?: return
        if(!ImportGate.canRecognize(s.mardConfirmed,s.gridConfirmed,s.legend)) {
            mutable.update { it.copy(message="请先确认网格已对齐、MARD 色卡及全部图例条目；无效或多余条目请删除。") };return
        }
        work("按图例逐格识别…") {
            val ctx=currentCoroutineContext()
            val recognized=withContext(Dispatchers.Default) {
                val bitmap=ChartImageDecoder.decode(store.source(id),s.region) { ctx.ensureActive() }
                try { val p=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(p,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                    CellRecognizer.recognize(p,bitmap.width,bitmap.height,s.grid,s.legend,{ctx.ensureActive()}) { progress -> mutable.update { it.copy(progress=progress) } } to bitmap
                } catch(t: Throwable) { bitmap.recycle();throw t }
            }
            ctx.ensureActive();val (draft,review)=recognized;history.clear();mutable.update { it.copy(draft=draft,pattern=null,reviewBitmap=review,detailBitmap=null,selected=nextPending(draft),canUndo=false,canRedo=false,message="完成。待确认 ${draft.pendingCount} 格；浅色格需区分白豆与空格。") }
        }
    }
    private fun nextPending(d: RecognitionDraft)=d.evidence.indices.filter { d[it]==PENDING_CELL }.minByOrNull { d.evidence[it].score } ?: 0
    fun select(index: Int) { if(!state.value.busy) mutable.update { it.copy(selected=index.coerceIn(0,it.grid.columns*it.grid.rows-1)) } }
    fun next() { state.value.draft?.let { select(nextPending(it)) } }
    private fun publishEdit(old: RecognitionDraft,new: RecognitionDraft) {
        history.record(old,new);mutable.update { it.copy(draft=new,pattern=null,canUndo=history.canUndo,canRedo=history.canRedo) }
    }
    fun edit(value: Int,batch: Boolean=false) {
        val s=state.value;if(s.busy) return;val d=s.draft ?: return;val i=s.selected
        val indices=if(!batch) intArrayOf(i) else d.evidence.indices.filter {
            if(d[i]==PENDING_CELL) d[it]==PENDING_CELL && d.evidence[it].suggested==d.evidence[i].suggested && d.evidence[it].reason.startsWith("白豆")==d.evidence[i].reason.startsWith("白豆") else d[it]==d[i]
        }.toIntArray()
        publishEdit(d,d.change(indices,value))
    }
    fun editCells(indices: IntArray,value: Int) {
        val s=state.value;if(s.busy) return;val d=s.draft ?: return
        if(indices.isEmpty()) return
        publishEdit(d,CellReview.changeSelected(d,indices,value))
    }
    fun clearCellDetail() { detailTask?.cancel();mutable.update { it.copy(detailBitmap=null,detailIndex=-1,detailMessage="") } }
    fun loadCellDetail() {
        clearCellDetail();val s=state.value;val id=s.id ?: return;if(s.draft==null) return
        detailTask=viewModelScope.launch {
            try {
                val ctx=currentCoroutineContext();val r=s.region;val c=CellReview.cellRegion(s.grid,s.selected)
                val region=ChartRegion(r.left+(r.right-r.left)*c.left,r.top+(r.bottom-r.top)*c.top,
                    r.left+(r.right-r.left)*c.right,r.top+(r.bottom-r.top)*c.bottom)
                val detail=withContext(Dispatchers.IO) { ChartImageDecoder.decode(store.source(id),region,maxEdge=512) {ctx.ensureActive()} }
                ctx.ensureActive()
                mutable.update { if(it.id==id&&it.region==s.region&&it.grid==s.grid&&it.selected==s.selected&&it.draft!=null)
                    it.copy(detailBitmap=detail,detailIndex=s.selected) else it }
            } catch(e: CancellationException) { throw e }
            catch(_: OutOfMemoryError) { mutable.update { it.copy(detailMessage="内存不足，暂用工作图预览。") } }
            catch(_: Exception) { mutable.update { it.copy(detailMessage="原文件小格暂不能解码，使用工作图预览。") } }
        }
    }
    fun acceptNonWhiteSuggestions() {
        val s=state.value;if(s.busy) return;val d=s.draft ?: return
        val cells=d.snapshot();for(i in cells.indices) if(cells[i]==PENDING_CELL && !d.evidence[i].reason.startsWith("白豆")) cells[i]=d.evidence[i].suggested
        publishEdit(d,RecognitionDraft(d.grid,cells,d.evidence))
    }
    fun undo(redo: Boolean=false) {
        if(state.value.busy) return
        val d=state.value.draft ?: return;val next=if(redo) history.redo(d) else history.undo(d)
        mutable.update { it.copy(draft=next,pattern=null,canUndo=history.canUndo,canRedo=history.canRedo) }
    }
    private fun project(s: ImportChartState)=ChartProject(s.name.ifBlank { "导入图纸" },s.hash,s.grid,s.region,s.legendRegion,s.legend,s.draft,s.mardConfirmed)
    fun save(confirm: Boolean=false) {
        val s=state.value;val id=s.id ?: return
        if(confirm && !ImportGate.canConfirm(s.mardConfirmed,s.gridConfirmed,s.draft)) { mutable.update { it.copy(message="须确认网格已对齐，且待确认格全部处理后才能导出") };return }
        work("保存可编辑工程…") {
            val pattern=if(confirm) s.draft!!.confirm() else null
            val items=withContext(Dispatchers.IO) { store.save(id,project(s));store.list() }
            mutable.update { it.copy(pattern=pattern ?: it.pattern,projects=items,message=if(confirm) "已确认并保存工程，可以导出 PNG。棋盘格表示空格，不计豆数。" else "工程已保存；退出后可从工程列表继续校对。撤销历史仅保留在本次会话。") }
        }
    }
    fun load(id: String)=work("打开工程并校验原图…") {
        val p=withContext(Dispatchers.IO) { store.load(id) };val bitmap=ImagePipeline.decode(resolver,Uri.fromFile(store.source(id)))
        val ctx=currentCoroutineContext()
        val review=if(p.draft!=null) withContext(Dispatchers.IO) { ChartImageDecoder.decode(store.source(id),p.region) {ctx.ensureActive()} } else null
        ctx.ensureActive();history.clear()
        mutable.update { ImportChartState(bitmap,id,p.sourceHash,p.name,p.grid,p.region,p.legendRegion,p.legend,p.mardConfirmed,p.draft,
            reviewBitmap=review,projects=it.projects,busy=true,message="工程已恢复。请重新核对并勾选网格已对齐；校对矩阵保留，确认后可导出。") }
    }
    fun discover(text: String)=work("提取公开网页中的图片链接…") {
        val ctx=currentCoroutineContext();val urls=withContext(Dispatchers.IO) { fetcher.discover(text) {ctx.ensureActive()} }
        state.value.downloaded?.delete()
        mutable.update { it.copy(urls=urls,downloaded=null,downloadPreview=null,message="找到 ${urls.size} 个候选。可能包含封面、广告或缩略图，请预览并选择完整图纸。") }
    }
    fun previewUrl(url: String)=work("下载所选候选图片…") {
        val ctx=currentCoroutineContext();val file=File.createTempFile("chart-link-",".img",getApplication<Application>().cacheDir)
        try {
            withContext(Dispatchers.IO) { fetcher.download(url,file) {ctx.ensureActive()} }
            val bitmap=ImagePipeline.decode(resolver,Uri.fromFile(file));ctx.ensureActive()
            val previous=state.value.downloaded;mutable.update { it.copy(downloaded=file,downloadPreview=bitmap,message="确认这张是所需的完整图纸，再点击导入。") };previous?.delete()
        } catch(t: Throwable) { file.delete();throw t }
    }
    fun useDownloaded() { state.value.downloaded?.let { pick(Uri.fromFile(it)) } }
    fun prepareExport(): Boolean {
        val s=state.value
        if(s.busy) return false
        val blockers=ChartExport.blockers(s.mardConfirmed,s.gridConfirmed,s.draft)
        if(blockers.isNotEmpty()) { mutable.update {it.copy(message="导出前还需：${blockers.joinToString("；")}")};return false }
        val pattern=ChartExport.snapshot(s.mardConfirmed,s.gridConfirmed,s.draft)
        exportSnapshot=pattern
        mutable.update {it.copy(pattern=pattern,message="当前校对已确认，请选择 PNG 保存位置。可编辑工程需另行保存。")}
        return true
    }
    fun export(uri: Uri?) {
        val pattern=exportSnapshot;exportSnapshot=null
        if(uri==null) { mutable.update {it.copy(message="已取消 PNG 导出；校对结果仍在，请保存工程以便续改。")};return }
        if(pattern==null) { mutable.update {it.copy(message="导出状态已失效，请重新打开工程并点击导出 PNG。")};return }
        work("保存 PNG 图纸…") { PatternRenderer.save(resolver,uri,pattern);mutable.update { it.copy(message="PNG 图纸已保存；空格不计豆数。如需下次续改，请另行保存工程。") } }
    }
}
