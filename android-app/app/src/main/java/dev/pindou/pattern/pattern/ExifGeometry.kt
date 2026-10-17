package dev.pindou.pattern.pattern

/** Maps normalized raw pixel coordinates to EXIF-upright coordinates, including mirrors. */
object ExifGeometry {
    fun point(x: Double, y: Double, orientation: Int): Pair<Double, Double> = when (orientation) {
        2 -> 1-x to y
        3 -> 1-x to 1-y
        4 -> x to 1-y
        5 -> y to x
        6 -> 1-y to x
        7 -> 1-y to 1-x
        8 -> y to 1-x
        else -> x to y
    }
    fun swapsAxes(orientation: Int) = orientation in 5..8
    fun inverse(orientation: Int) = when (orientation) { 6 -> 8; 8 -> 6; else -> orientation }
    fun rect(rect: CropRect, orientation: Int): CropRect {
        val points = listOf(point(rect.left,rect.top,orientation), point(rect.left+rect.width,rect.top,orientation),
            point(rect.left,rect.top+rect.height,orientation), point(rect.left+rect.width,rect.top+rect.height,orientation))
        val left = points.minOf { it.first }; val top = points.minOf { it.second }
        return CropRect(left,top,points.maxOf { it.first }-left,points.maxOf { it.second }-top)
    }
}
