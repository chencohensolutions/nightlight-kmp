package com.chencohensolutions.nightlight

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

/**
 * Runs the flame over time. Unlike the stateless [FlameModel] functions it accumulates the
 * breath phase, so changing the breathing speed changes the pace from where the breath is
 * instead of jumping to another point of the cycle, and it eases in and out of Steady mode.
 *
 * Not thread-safe: call [step] from one thread (the UI frame loop).
 */
class FlameAnimator {
    /** Breath position, in breaths (kept in 0..1). */
    private var phase = 0.0
    /** Clock for flicker, gusts and sway. */
    private var time = 0.0
    /** Breaths per second, eased toward the selected mode's pace. */
    private var rate = 0.0
    /** 0 = steady light, 1 = fully animated. */
    private var amount = 0f
    private var started = false

    /** True once the light is steady and nothing is easing: the frame loop can stop. */
    val isSettled: Boolean get() = started && amount == 0f

    /**
     * Advance by [dtSeconds] (0 just recomputes the frame for new settings) and return what
     * to paint.
     */
    fun step(dtSeconds: Double, settings: NightLightSettings): FlameFrame {
        val animated = FlameModel.isAnimated(settings)
        val targetAmount = if (animated) 1f else 0f
        if (!started) {
            started = true
            amount = targetAmount
            if (animated) rate = 1.0 / settings.breathing.periodSeconds
        }

        // A long gap means the app was paused; don't leap ahead.
        val dt = if (dtSeconds.isNaN()) 0.0 else dtSeconds.coerceIn(0.0, MAX_STEP_SECONDS)
        if (dt > 0.0 && (animated || amount > 0f)) {
            if (animated) {
                val target = 1.0 / settings.breathing.periodSeconds
                rate = if (rate <= 0.0) target else rate + (target - rate) * ease(dt, RATE_EASE_SECONDS)
            }
            phase += rate * dt
            phase -= floor(phase)
            time += dt

            amount += (targetAmount - amount) * ease(dt, AMOUNT_EASE_SECONDS).toFloat()
            if (abs(targetAmount - amount) < SNAP) amount = targetAmount
        }

        val steady = FlameModel.STEADY_INTENSITY
        val intensity = if (amount == 0f) {
            steady
        } else {
            steady + amount * (FlameModel.intensity(phase, time) - steady)
        }
        return FlameModel.frame(settings, intensity, time, amount)
    }

    private fun ease(dt: Double, timeConstant: Double): Double = 1.0 - exp(-dt / timeConstant)

    private companion object {
        const val MAX_STEP_SECONDS = 0.1
        const val RATE_EASE_SECONDS = 1.5
        const val AMOUNT_EASE_SECONDS = 0.6
        const val SNAP = 0.004f
    }
}

/**
 * Decides which display frames to render. With a limit, frames closer together than the
 * target interval are skipped; without one every frame is rendered.
 */
class FramePacer(maxFps: Int = 0) {
    // A little under the exact interval so vsync jitter doesn't drop every other frame.
    private val minIntervalNanos: Long = if (maxFps > 0) 1_000_000_000L / maxFps * 9 / 10 else 0L
    private var last = 0L
    private var hasLast = false

    fun shouldRender(frameTimeNanos: Long): Boolean {
        if (hasLast && frameTimeNanos - last < minIntervalNanos) return false
        last = frameTimeNanos
        hasLast = true
        return true
    }

    companion object {
        const val BATTERY_SAVER_FPS = 30
    }
}
