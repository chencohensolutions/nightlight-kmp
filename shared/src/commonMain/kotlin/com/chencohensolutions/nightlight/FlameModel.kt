package com.chencohensolutions.nightlight

import kotlin.math.exp
import kotlin.math.floor

/**
 * What to paint for one frame: a soft glow that fades from [core] at its center to
 * [edge] at [radius] and beyond.
 *
 * @param centerX glow center as a fraction of the view width (0.5 = middle).
 * @param centerY glow center as a fraction of the view height.
 * @param radius glow radius as a fraction of the view's larger dimension.
 */
data class FlameFrame(
    val core: Rgb,
    val edge: Rgb,
    val intensity: Float,
    val centerX: Float = FlameModel.CENTER_X,
    val centerY: Float = FlameModel.CENTER_Y,
    val radius: Float = 0.75f,
) {
    /** Color at [x] = distance from the center / radius. Same profile the renderer draws. */
    fun colorAt(x: Float): Rgb = edge.mix(core, FlameModel.falloff(x))
}

/**
 * Pure flame math. Everything here is a function of its arguments; the running state
 * (breath phase, easing between modes) lives in [FlameAnimator].
 *
 * All levels are display-encoded (gamma) values, i.e. roughly perceptual lightness, not
 * linear light: a level of 0.78 is about 58 % of the light, 0.4 about 13 %.
 */
object FlameModel {
    /** Darkest point of a breath, as a fraction of full level. */
    const val BREATH_FLOOR = 0.78f
    const val BREATH_DEPTH = 0.20f
    /** Level in Steady mode: the middle of a breath, so switching modes doesn't change brightness. */
    const val STEADY_INTENSITY = 0.9f
    /** +-2 % level is about +-5 % light: visible as life, not as flashing. */
    private const val FLICKER_DEPTH = 0.02f
    /** Lowest level a gust can push the flame to. */
    const val MIN_INTENSITY = 0.65f

    /** Share of a breath spent brightening (inhale); the rest is a longer, calmer exhale. */
    private const val INHALE = 0.4

    /**
     * A dimmer flame burns redder. Kelvin change per unit of level around [STEADY_INTENSITY]:
     * about +100 K at the top of a breath and -150 K at the bottom.
     */
    private const val KELVIN_PER_LEVEL = 1250f
    /** The halo around a flame is cooler soot glow: redder than the core. */
    private const val HALO_KELVIN_DROP = 300f
    private const val EDGE_RATIO = 0.4f

    const val CENTER_X = 0.5f
    const val CENTER_Y = 0.6f
    private const val SWAY_X = 0.015f
    private const val SWAY_Y = 0.010f
    private const val RADIUS_BASE = 0.70f
    private const val RADIUS_PER_LEVEL = 0.07f

    /** Width of the Gaussian glow profile, in radius units. */
    const val FALLOFF_SIGMA = 0.36f
    private val FALLOFF_AT_EDGE = gaussian(1f)

    /** One gust of air per slot, at a random moment inside it. */
    private const val GUST_SLOT_SECONDS = 30.0
    private const val GUST_ATTACK_SECONDS = 0.25
    private const val GUST_DECAY_SECONDS = 1.2
    private const val GUST_FLICKER_BOOST = 2f
    private const val GUST_DIP = 0.05f

    /**
     * Breath shape for [phase] in breaths (any value; only the fraction matters). 0..1.
     * Rises for 40 % of the cycle and falls for 60 %, with zero slope and curvature at both
     * turning points so there is no visible "corner".
     */
    fun breath(phase: Double): Float {
        val p = phase - floor(phase)
        val x = if (p < INHALE) p / INHALE else 1.0 - (p - INHALE) / (1.0 - INHALE)
        return (x * x * x * (x * (x * 6.0 - 15.0) + 10.0)).toFloat()
    }

    /**
     * Organic flicker in -1..1: three octaves of value noise (0.35, 0.9, 2.2 Hz) with
     * amplitude falling with frequency, like the slow drift of a still candle. Nothing
     * above ~2 Hz, so it never reads as flashing.
     */
    fun flicker(t: Double): Float =
        (noise(t * 0.35, 1) + 0.5f * noise(t * 0.9, 2) + 0.25f * noise(t * 2.2, 3)) / 1.75f

    /** 0..1 envelope of the occasional gust of air: quick rise, ~1 s decay. */
    fun gust(t: Double): Float {
        if (t < 0.0) return 0f
        val slot = floor(t / GUST_SLOT_SECONDS)
        val k = slot.toLong().toInt()
        // Starts in the first 70 % of the slot so it has fully died down before the next one.
        val start = (slot + 0.1 + 0.6 * hash01(k, 11)) * GUST_SLOT_SECONDS
        val dt = t - start
        if (dt <= 0.0) return 0f
        val strength = 0.5f + 0.5f * hash01(k, 12)
        val envelope = if (dt < GUST_ATTACK_SECONDS) {
            val x = dt / GUST_ATTACK_SECONDS
            x * x * (3.0 - 2.0 * x)
        } else {
            exp(-(dt - GUST_ATTACK_SECONDS) / GUST_DECAY_SECONDS)
        }
        return strength * envelope.toFloat()
    }

    /** Animated level, [MIN_INTENSITY]..1, for a breath [phase] (in breaths) at time [t]. */
    fun intensity(phase: Double, t: Double): Float {
        val g = gust(t)
        val level = BREATH_FLOOR + BREATH_DEPTH * breath(phase) +
            FLICKER_DEPTH * (1f + GUST_FLICKER_BOOST * g) * flicker(t) -
            GUST_DIP * g
        return level.coerceIn(MIN_INTENSITY, 1f)
    }

    /** Stateless level at time [t]; Steady mode is constant. See [FlameAnimator] for smooth mode changes. */
    fun intensity(breathing: Breathing, t: Double): Float =
        if (breathing == Breathing.Off) STEADY_INTENSITY else intensity(t / breathing.periodSeconds, t)

    /** Stateless frame at time [timeSeconds]. */
    fun frame(settings: NightLightSettings, timeSeconds: Double): FlameFrame {
        val animated = isAnimated(settings)
        return frame(
            settings = settings,
            intensity = intensity(settings.breathing, timeSeconds),
            timeSeconds = timeSeconds,
            motion = if (animated) 1f else 0f,
        )
    }

    /**
     * Colors and geometry for a given [intensity].
     *
     * @param motion 0..1: how much the glow sways (0 in Steady mode).
     */
    fun frame(settings: NightLightSettings, intensity: Float, timeSeconds: Double, motion: Float): FlameFrame {
        val s = settings.normalized()
        val kelvin = s.kelvin +
            Brightness.kelvinShift(s.brightness) +
            (intensity - STEADY_INTENSITY) * KELVIN_PER_LEVEL
        val gain = Brightness.pixelGain(s.brightness) * intensity
        val core = BlueLightFilter.apply(ColorTemperature.toRgb(kelvin), s.blueFilter)
        val halo = BlueLightFilter.apply(ColorTemperature.toRgb(kelvin - HALO_KELVIN_DROP), s.blueFilter)

        val m = motion.coerceIn(0f, 1f)
        // A gust pushes the flame sideways as well as dimming it.
        val sway = m * (1f + gust(timeSeconds))
        return FlameFrame(
            core = core * gain,
            edge = halo * (gain * EDGE_RATIO * Brightness.edgeLevel(s.brightness)),
            intensity = intensity,
            centerX = CENTER_X + SWAY_X * sway * drift(timeSeconds, 0.13, 0.31, 21),
            centerY = CENTER_Y + SWAY_Y * sway * drift(timeSeconds, 0.11, 0.23, 31),
            radius = (RADIUS_BASE + RADIUS_PER_LEVEL * intensity) * Brightness.glowScale(s.brightness),
        )
    }

    /**
     * Glow profile: 1 at the center, 0 at [x] >= 1. A Gaussian (rescaled to reach exactly 0
     * at the radius) instead of a straight ramp, so the center has no pointed peak.
     */
    fun falloff(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return ((gaussian(c) - FALLOFF_AT_EDGE) / (1f - FALLOFF_AT_EDGE)).coerceIn(0f, 1f)
    }

    /** Whether frames must be redrawn continuously. */
    fun isAnimated(settings: NightLightSettings) = settings.breathing != Breathing.Off

    private fun gaussian(x: Float): Float = exp(-x * x / (2f * FALLOFF_SIGMA * FALLOFF_SIGMA))

    /** Slow two-octave wander in -1..1. */
    private fun drift(t: Double, f1: Double, f2: Double, seed: Int): Float =
        (noise(t * f1, seed) + 0.4f * noise(t * f2, seed + 1)) / 1.4f

    /** Smooth 1-D value noise in -1..1; a pure function of [x], so it never needs state. */
    private fun noise(x: Double, seed: Int): Float {
        val cell = floor(x)
        val i = cell.toLong().toInt()
        val f = (x - cell).toFloat()
        val u = f * f * f * (f * (f * 6f - 15f) + 10f)
        val a = hash01(i, seed) * 2f - 1f
        val b = hash01(i + 1, seed) * 2f - 1f
        return a + (b - a) * u
    }

    /** Integer hash -> 0..1. Int overflow wraps the same way on every Kotlin target. */
    private fun hash01(n: Int, seed: Int): Float {
        var h = n * 374761393 + seed * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7fffffff).toFloat() / 2147483647f
    }
}
