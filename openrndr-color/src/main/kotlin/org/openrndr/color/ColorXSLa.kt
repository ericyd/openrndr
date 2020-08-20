package org.openrndr.color

import org.openrndr.math.mixAngle

data class ColorXSLa(val x: Double, val s: Double, val l: Double, val a: Double):
        ConvertibleToColorRGBa,
        ShadableColor<ColorXSLa>,
        HueShiftableColor<ColorXSLa>,
        SaturatableColor<ColorXSLa>,
        OpacifiableColor<ColorXSLa>,
        AlgebraicColor<ColorXSLa> {

    companion object {
        fun fromHSLa(hsla: ColorHSLa): ColorXSLa {
            val h = ((hsla.h % 360.0) + 360.0) % 360.0
            val x = if (0 <= h && h < 35) {
                map(h, 0.0, 35.0, 0.0, 60.0)
            } else if (35 <= h && h < 60) {
                map(h, 35.0, 60.0, 60.0, 120.0)
            } else if (60 <= h && h < 135.0) {
                map(h, 60.0, 135.0, 120.0, 180.0)
            } else if (135.0 <= h && h < 225.0) {
                map(h, 135.0, 225.0, 180.0, 240.0)
            } else if (225.0 <= h && h < 275.0) {
                map(h, 225.0, 275.0, 240.0, 300.0)
            } else {
                map(h, 276.0, 360.0, 300.0, 360.0)
            }
            return ColorXSLa(x, hsla.s, hsla.l, hsla.a)
        }
    }

    fun toHSLa(): ColorHSLa {
        val x = this.x % 360.0
        val h = if (0.0 <= x && x < 60.0) {
            map(x, 0.0, 60.0, 0.0, 35.0)
        } else if (60.0 <= x && x < 120.0) {
            map(x, 60.0, 120.0, 35.0, 60.0)
        } else if (120.0 <= x && x < 180.0) {
            map(x, 120.0, 180.0, 60.0, 135.0)
        } else if (180.0 <= x && x < 240.0) {
            map(x, 180.0, 240.0, 135.0, 225.0)
        } else if (240.0 <= x && x < 300.0) {
            map(x, 240.0, 300.0, 225.0, 275.0)
        } else {
            map(x, 300.0, 360.0, 276.0, 360.0)
        }
        return ColorHSLa(h, s, l, a)
    }

    override fun toRGBa() = toHSLa().toRGBa()

    override fun shiftHue(shiftInDegrees: Double) = copy(x = (x + shiftInDegrees))
    override fun saturate(factor: Double) = copy(s = s * factor)
    override fun shade(factor: Double) = copy(l = l * factor)
    override fun opacify(factor: Double) = copy(a = a * factor)

    override fun plus(other: ColorXSLa) = copy(x = x + other.x, s = s + other.s, l = l + other.l, a = a + other.a)
    override fun minus(other: ColorXSLa) = copy(x = x - other.x, s = s - other.s, l = l - other.l, a = a - other.a)
    override fun times(factor: Double) = copy(x = x * factor, s = s * factor, l = l * factor, a = a * factor)

    override fun mix(other: ColorXSLa, factor: Double) = mix(this, other, factor)
}

private fun map(x: Double, a: Double, b: Double, c: Double, d: Double): Double {
    return ((x - a) / (b - a)) * (d - c) + c
}

/**
 * Mixes two colors in XSLa space
 * @param left the left hand ColorXSLa color
 * @param right the right hand ColorXSLa
 * @param x the mix amount
 * @return a mix of [left] and [right], x == 0.0 corresponds with left, x == 1.0 corresponds with right
 */
fun mix(left: ColorXSLa, right: ColorXSLa, x: Double): ColorXSLa {
    val sx = x.coerceIn(0.0, 1.0)
    return ColorXSLa(
            mixAngle(left.x, right.x, sx),
            (1.0 - sx) * left.s + sx * right.s,
            (1.0 - sx) * left.l + sx * right.l,
            (1.0 - sx) * left.a + sx * right.a)
}
