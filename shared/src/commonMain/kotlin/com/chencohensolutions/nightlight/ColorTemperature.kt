package com.chencohensolutions.nightlight

import kotlin.math.ln
import kotlin.math.pow

/**
 * Display-encoded (gamma) sRGB color, each channel 0..1: the values that go to the screen.
 *
 * Scaling by k therefore scales perceived lightness by about k, and the emitted light by
 * about k^2.2 (0.5 is roughly 22 % of the light, not half).
 */
data class Rgb(val r: Float, val g: Float, val b: Float) {
    operator fun times(k: Float) = Rgb(
        (r * k).coerceIn(0f, 1f),
        (g * k).coerceIn(0f, 1f),
        (b * k).coerceIn(0f, 1f),
    )

    /** Blend toward [other]: t = 0 is this color, t = 1 is [other]. */
    fun mix(other: Rgb, t: Float): Rgb {
        val k = t.coerceIn(0f, 1f)
        return Rgb(r + (other.r - r) * k, g + (other.g - g) * k, b + (other.b - b) * k)
    }

    /** 0xAARRGGBB, opaque. */
    fun toArgb(): Long {
        fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
        return (0xFFL shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}

object ColorTemperature {
    /** Deep ember. */
    const val MIN_KELVIN = 1200f
    /** Soft warm white. */
    const val MAX_KELVIN = 4000f
    /** A real candle flame is ~1850–1900 K. */
    const val CANDLE_KELVIN = 1900f

    /**
     * Black-body approximation (Tanner Helland's fit), good for 1000–40000 K.
     */
    fun toRgb(kelvin: Float): Rgb {
        val t = kelvin.coerceIn(1000f, 40000f) / 100.0

        val red = if (t <= 66) 255.0 else 329.698727446 * (t - 60).pow(-0.1332047592)

        val green = if (t <= 66) {
            99.4708025861 * ln(t) - 161.1195681661
        } else {
            288.1221695283 * (t - 60).pow(-0.0755148492)
        }

        val blue = when {
            t >= 66 -> 255.0
            t <= 19 -> 0.0
            else -> 138.5177312231 * ln(t - 10) - 305.0447927307
        }

        fun norm(v: Double) = (v.coerceIn(0.0, 255.0) / 255.0).toFloat()
        return Rgb(norm(red), norm(green), norm(blue))
    }

    /** UI warmth slider (0 = coolest, 1 = warmest) -> Kelvin. */
    fun warmthToKelvin(warmth: Float): Float =
        MAX_KELVIN - warmth.coerceIn(0f, 1f) * (MAX_KELVIN - MIN_KELVIN)

    /** Kelvin -> UI warmth slider (0 = coolest, 1 = warmest). */
    fun kelvinToWarmth(kelvin: Float): Float =
        ((MAX_KELVIN - kelvin.coerceIn(MIN_KELVIN, MAX_KELVIN)) / (MAX_KELVIN - MIN_KELVIN))

    fun label(kelvin: Float): String = when {
        kelvin < 1600f -> "Ember"
        kelvin < 2100f -> "Candle"
        kelvin < 2700f -> "Sunset"
        kelvin < 3300f -> "Lamp"
        else -> "Warm white"
    }
}
