package dev.pindou.pattern.pattern

import kotlin.math.*

data class Lab(val l: Double, val a: Double, val b: Double)
object ColorMath {
    fun linear(v: Double): Double = if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
    fun rgbToLab(rgb: Int): Lab = linearToLab(linear((rgb shr 16 and 255)/255.0), linear((rgb shr 8 and 255)/255.0), linear((rgb and 255)/255.0))
    fun linearToLab(r: Double, g: Double, b: Double): Lab {
        fun f(t: Double): Double = if (t > 216.0/24389) Math.cbrt(t) else (24389.0/27*t+16)/116
        val x = f((.4124564*r + .3575761*g + .1804375*b)/.95047)
        val y = f(.2126729*r + .7151522*g + .0721750*b)
        val z = f((.0193339*r + .1191920*g + .9503041*b)/1.08883)
        return Lab(116*y-16, 500*(x-y), 200*(y-z))
    }
    // CIEDE2000, kL=kC=kH=1. Hue angles are in degrees.
    fun deltaE00(x: Lab, y: Lab): Double {
        fun cosd(v: Double) = cos(Math.toRadians(v))
        fun sind(v: Double) = sin(Math.toRadians(v))
        fun hue(a: Double, b: Double): Double = if (a == 0.0 && b == 0.0) 0.0 else (Math.toDegrees(atan2(b,a))+360)%360
        val c1 = hypot(x.a,x.b); val c2 = hypot(y.a,y.b)
        val cm = (c1+c2)/2; val p = cm.pow(7)
        val g = .5*(1-sqrt(p/(p+25.0.pow(7))))
        val a1 = (1+g)*x.a; val a2 = (1+g)*y.a
        val cp1 = hypot(a1,x.b); val cp2 = hypot(a2,y.b)
        val h1 = hue(a1,x.b); val h2 = hue(a2,y.b)
        val dl = y.l-x.l; val dc = cp2-cp1
        val dh = when { cp1*cp2 == 0.0 -> 0.0; abs(h2-h1)<=180 -> h2-h1; h2-h1>180 -> h2-h1-360; else -> h2-h1+360 }
        val dH = 2*sqrt(cp1*cp2)*sind(dh/2)
        val lm = (x.l+y.l)/2; val cpm = (cp1+cp2)/2
        val hm = when { cp1*cp2==0.0 -> h1+h2; abs(h1-h2)<=180 -> (h1+h2)/2; h1+h2<360 -> (h1+h2+360)/2; else -> (h1+h2-360)/2 }
        val t = 1-.17*cosd(hm-30)+.24*cosd(2*hm)+.32*cosd(3*hm+6)-.20*cosd(4*hm-63)
        val sl = 1+.015*(lm-50).pow(2)/sqrt(20+(lm-50).pow(2))
        val sc = 1+.045*cpm; val sh = 1+.015*cpm*t
        val rt = -2*sqrt(cpm.pow(7)/(cpm.pow(7)+25.0.pow(7)))*sind(60*exp(-((hm-275)/25).pow(2)))
        return sqrt(max(0.0,(dl/sl).pow(2)+(dc/sc).pow(2)+(dH/sh).pow(2)+rt*(dc/sc)*(dH/sh)))
    }
}
