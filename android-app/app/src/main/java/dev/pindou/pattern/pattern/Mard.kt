package dev.pindou.pattern.pattern

object Mard {
    const val SOURCE = "https://github.com/maxcleme/beadcolors"
    const val COMMIT = "f97ff4283d03cef5cd7e1071a86f5892e0c0c61b"
    val palette = PaletteRevision("mard-291-beadcolors-f97ff42-v1", MardPaletteData.colors)
    val labs = palette.colors.map { ColorMath.rgbToLab(it.displayRgb) }
    val backgroundIndex = palette.colors.indexOfFirst { it.code=="H2" }.also { require(it>=0) }
    val backgroundRgb = palette.colors[backgroundIndex].displayRgb
    fun indexOf(code: String): Int = palette.colors.indexOfFirst { it.code==code }.also { require(it>=0) }
    fun nearest(lab: Lab): Int {
        var best=0; var distance=Double.POSITIVE_INFINITY
        for(i in labs.indices) {
            val candidate=ColorMath.deltaE00(lab,labs[i])
            if(candidate<distance) { best=i; distance=candidate }
            if(distance==0.0) break
        }
        return best
    }
}
/** Per-generation exact Lab memoization; never rounds colors or changes the nearest-color result. */
class PaletteMatcher {
    private val cache=HashMap<Lab,Int>()
    fun nearest(lab: Lab): Int = cache[lab] ?: Mard.nearest(lab).also {
        if(cache.size<16384) cache[lab]=it
    }
}
