package com.chencohensolutions.nightlight

/**
 * In-app blue light reduction, applied after the color temperature.
 *
 * Works like a system "eye comfort" filter: blue is cut hardest, green partly
 * (green still stimulates the melanopsin cells that keep you awake), red is kept.
 * At full strength the light becomes a deep amber.
 */
object BlueLightFilter {
    private const val GREEN_CUT = 0.45f

    fun apply(color: Rgb, strength: Float): Rgb {
        val s = strength.coerceIn(0f, 1f)
        return Rgb(
            r = color.r,
            g = color.g * (1f - GREEN_CUT * s),
            b = color.b * (1f - s),
        )
    }
}
