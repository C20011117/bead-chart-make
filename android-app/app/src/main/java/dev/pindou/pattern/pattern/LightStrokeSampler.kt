package dev.pindou.pattern.pattern

import kotlin.math.*

/** Preserves a source light stroke between dark sides, never synthesizes or snaps a color. */
object LightStrokeSampler {
    private val directions=arrayOf(1 to 0,0 to 1,1 to 1,1 to -1)
    fun sample(pixels: IntArray, width: Int, height: Int, rect: CropRect,
        linear: DoubleArray, background: DoubleArray): LinearSample? {
        fun color(x: Int,y: Int): LinearSample {
            val pixel=pixels[y*width+x];val a=(pixel ushr 24)/255.0
            return LinearSample(linear[pixel shr 16 and 255]*a+background[0]*(1-a),
                linear[pixel shr 8 and 255]*a+background[1]*(1-a),linear[pixel and 255]*a+background[2]*(1-a))
        }
        val x0=floor(rect.left).toInt().coerceAtLeast(0);val y0=floor(rect.top).toInt().coerceAtLeast(0)
        val x1=ceil(rect.left+rect.width).toInt().coerceAtMost(width);val y1=ceil(rect.top+rect.height).toInt().coerceAtMost(height)
        val w=x1-x0;val h=y1-y0
        if(w<=0 || h<=0) return null
        val radii=intArrayOf(max(1,(min(rect.width,rect.height)*.2).roundToInt()),
            max(2,(min(rect.width,rect.height)*.4).roundToInt()))
        val mask=BooleanArray(w*h)
        var weightSum=0.0;var red=0.0;var green=0.0;var blue=0.0
        var sx=0.0;var sy=0.0;var sxx=0.0;var syy=0.0;var sxy=0.0
        var minX=width;var maxX=-1;var minY=height;var maxY=-1
        var lightArea=0.0
        for(y in y0 until y1) for(x in x0 until x1) {
            val c=color(x,y)
            if(c.luminance<.65 || c.spread>.2) continue
            val weight=(min(x+1.0,rect.left+rect.width)-max(x.toDouble(),rect.left))*
                (min(y+1.0,rect.top+rect.height)-max(y.toDouble(),rect.top))
            lightArea+=weight
            val ridge=directions.any { (dx,dy) -> radii.any { radius ->
                val ax=x-dx*radius;val ay=y-dy*radius;val bx=x+dx*radius;val by=y+dy*radius
                ax in 0 until width && bx in 0 until width && ay in 0 until height && by in 0 until height &&
                    max(color(ax,ay).luminance,color(bx,by).luminance)<.22
            } }
            if(!ridge) continue
            mask[(y-y0)*w+x-x0]=true
            weightSum+=weight;red+=c.r*weight;green+=c.g*weight;blue+=c.b*weight
            val xx=x+.5-rect.left;val yy=y+.5-rect.top
            sx+=xx*weight;sy+=yy*weight;sxx+=xx*xx*weight;syy+=yy*yy*weight;sxy+=xx*yy*weight
            minX=min(minX,x);maxX=max(maxX,x);minY=min(minY,y);maxY=max(maxY,y)
        }
        val area=rect.width*rect.height
        if(lightArea/area>.50 || weightSum/area<.04 ||
            max((maxX-minX+1)/rect.width,(maxY-minY+1)/rect.height)<.65) return null
        // Several parallel strings must not merge into a broad white band.
        val queue=IntArray(mask.size);var components=0
        for(start in mask.indices) {
            if(!mask[start]) continue
            var head=0;var tail=1;queue[0]=start;mask[start]=false
            var lx=w;var rx=-1;var ty=h;var by=-1
            while(head<tail) {
                val i=queue[head++];val x=i%w;val y=i/w
                lx=min(lx,x);rx=max(rx,x);ty=min(ty,y);by=max(by,y)
                for(dy in -1..1) for(dx in -1..1) {
                    val nx=x+dx;val ny=y+dy
                    if(nx !in 0 until w || ny !in 0 until h) continue
                    val next=ny*w+nx
                    if(mask[next]) { mask[next]=false;queue[tail++]=next }
                }
            }
            if(tail/area>=.015 && max((rx-lx+1)/rect.width,(by-ty+1)/rect.height)>=.3) components++
        }
        if(components!=1) return null
        val mx=sx/weightSum;val my=sy/weightSum
        val vx=(sxx/weightSum-mx*mx).coerceAtLeast(0.0)
        val vy=(syy/weightSum-my*my).coerceAtLeast(0.0);val cov=sxy/weightSum-mx*my
        val small=(vx+vy-sqrt((vx-vy).pow(2)+4*cov*cov))/2
        val cell=min(rect.width,rect.height)
        if(small>(cell*.12).pow(2)) return null
        val angle=.5*atan2(2*cov,vx-vy)
        val distance=abs(-(mx-rect.width/2)*sin(angle)+(my-rect.height/2)*cos(angle))
        if(distance>cell*.45) return null
        return LinearSample(red/weightSum,green/weightSum,blue/weightSum)
    }
}
