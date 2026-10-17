package dev.pindou.pattern.feature.convert

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pindou.pattern.pattern.*
import dev.pindou.pattern.image.ImagePipeline
import dev.pindou.pattern.export.PatternRenderer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ConvertState(val bitmap: Bitmap? = null, val grid: GridSize = GridSize(64,64), val crop: CropSpec = CropSpec(fitImage=true),
    val options: ConversionOptions = ConversionOptions(),
    val pattern: Pattern? = null, val busy: Boolean = false, val progress: Float = 0f, val message: String? = null, val operation: String? = null)
class ConvertViewModel(app: Application): AndroidViewModel(app) {
    private val mutable = MutableStateFlow(ConvertState())
    val state = mutable.asStateFlow()
    private var task: Job? = null
    private var exportSnapshot: Pattern? = null
    private var sourceUri: Uri? = null
    private val resolver get() = getApplication<Application>().contentResolver
    private fun work(operation: String, block: suspend ()->Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy=true,progress=0f,message=null,operation=operation) }
        task = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { mutable.update { it.copy(message=if(operation=="保存图纸") "保存已取消，目标文件可能不完整，请检查后重试" else "操作已取消") }; throw e }
            catch (e: OutOfMemoryError) { mutable.update { it.copy(message="内存不足，请换用较小图片或降低网格") } }
            catch (e: Exception) { mutable.update { it.copy(message=if(operation=="保存图纸") "PNG 保存失败，请换一个保存位置重试" else "图片无法处理，请重选 JPEG、PNG 或 WebP 图片") } }
            finally { mutable.update { it.copy(busy=false,operation=null) } }
        }
    }
    fun pick(uri: Uri) = work("读取图片") {
        val bitmap = ImagePipeline.decode(resolver,uri)
        sourceUri=uri
        mutable.update { it.copy(bitmap=bitmap,crop=CropSpec(fitImage=true),pattern=null) }
        // Previous displayed bitmap is left to GC so Compose never draws a recycled image.
    }
    fun grid(columns: Int, rows: Int) { if (!mutable.value.busy) mutable.update { it.copy(grid=GridSize(columns,rows),pattern=null) } }
    fun crop(crop: CropSpec) { if (!mutable.value.busy) mutable.update { it.copy(crop=crop,pattern=null) } }
    fun options(options: ConversionOptions) { if(!mutable.value.busy) mutable.update { it.copy(options=options,pattern=null) } }
    fun generate() {
        val snapshot = mutable.value; val uri = sourceUri ?: return
        work("生成图纸") {
            val pattern = ImagePipeline.generate(resolver,uri,snapshot.grid,snapshot.crop,snapshot.options) { p -> mutable.update { it.copy(progress=p) } }
            mutable.update { it.copy(pattern=pattern,message="图纸已生成，放大可查看色号") }
        }
    }
    fun prepareExport(): Boolean { exportSnapshot=mutable.value.pattern; return exportSnapshot!=null }
    fun save(uri: Uri?) {
        val snapshot = exportSnapshot; exportSnapshot=null
        if (uri==null || snapshot==null) return
        work("保存图纸") { PatternRenderer.save(resolver,uri,snapshot); mutable.update { it.copy(message="PNG 已保存") } }
    }
    fun cancel() { task?.cancel() }
}
