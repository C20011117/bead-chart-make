package dev.pindou.pattern.pattern

/** Pure core shared by Android and desktop regression. Callbacks also make cancellation testable. */
object PatternGenerator {
    fun generate(pixels: IntArray, width: Int, height: Int, bounds: CropRect, grid: GridSize,
        options: ConversionOptions, checkActive: ()->Unit = {}, progress: (Float)->Unit = {}): Pattern {
        require(width>0 && height>0 && pixels.size==width*height)
        val cells=IntArray(grid.columns*grid.rows)
        val matcher=PaletteMatcher()
        val sampler=IllustrationSampler()
        val samples=Array(cells.size) { LinearSample(1.0,1.0,1.0) }
        for(y in 0 until grid.rows) {
            checkActive()
            for(x in 0 until grid.columns) {
                if(x%8==0) checkActive()
                val rect=CropRect(bounds.left+x*bounds.width/grid.columns,bounds.top+y*bounds.height/grid.rows,
                    bounds.width/grid.columns,bounds.height/grid.rows)
                val i=y*grid.columns+x
                if(options.style==SamplingStyle.PHOTO) cells[i]=matcher.nearest(AreaSampler.sample(pixels,width,height,rect))
                else samples[i]=sampler.sample(pixels,width,height,rect)
            }
            progress((y+1f)/grid.rows*if(options.style==SamplingStyle.PHOTO) 1f else .35f)
        }
        if(options.style==SamplingStyle.ILLUSTRATION) for(y in 0 until grid.rows) {
            checkActive()
            for(x in 0 until grid.columns) {
                if(x%8==0) checkActive()
                val i=y*grid.columns+x
                cells[i]=matcher.nearest(samples[i].lab())
            }
            progress(.35f+.65f*(y+1f)/grid.rows)
        }
        checkActive()
        return Pattern(grid,cells)
    }
}
