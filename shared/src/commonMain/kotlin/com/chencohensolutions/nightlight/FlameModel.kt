package com.chencohensolutions.nightlight

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What to paint for one frame: a bright core fading to a darker edge. */
data class FlameFrame(val core: Rgb, val edge: Rgb, val intensity: Float)

object FlameModel {
    /** Darkest point of a breath, as a fraction of full intensity. */
    private const val BREATH_FLOOR = 0.78f
    private const val BREATH_DEPTH = 0.18f
    private const val FLICKER_DEPTH = 0.04f
    /** A dimmer flame burns redder: Kelvin drop at the bottom of a breath. */
    private const val KELVIN_SAG = 140f
    private const val EDGE_RATIO = 0.4f

    fun frame(settings: NightLightSettings, timeSeconds: Double): FlameFrame {
        val s = settings.normalized()
        val intensity = intensity(s.breathing, timeSeconds)
        val kelvin = (s.kelvin - (1f - intensity) * KELVIN_SAG)
            .coerceAtLeast(ColorTemperature.MIN_KELVIN - KELVIN_SAG)
        val gain = Brightness.pixelGain(s.brightness) * intensity
        val base = BlueLightFilter.apply(ColorTemperature.toRgb(kelvin), s.blueFilter)
        return FlameFrame(core = base * gain, edge = base * (gain * EDGE_RATIO), intensity = intensity)
    }

    /** 0..1: slow breath plus a subtle organic flicker. Steady mode is always 1. */
    fun intensity(breathing: Breathing, t: Double): Float {
        if (breathing == Breathing.Off) return 1f
        val breath = 0.5 - 0.5 * cos(2 * PI * t / breathing.periodSeconds) // 0..1
        // Incommensurate frequencies so the flicker never visibly repeats.
        val flicker = (sin(t * 7.3) + 0.6 * sin(t * 11.7 + 1.3) + 0.3 * sin(t * 17.9 + 0.4)) / 1.9 // -1..1
        return (BREATH_FLOOR + BREATH_DEPTH * breath + FLICKER_DEPTH * flicker)
            .toFloat().coerceIn(0f, 1f)
    }

    /** Whether frames must be redrawn continuously. */
    fun isAnimated(settings: NightLightSettings) = settings.breathing != Breathing.Off
}
