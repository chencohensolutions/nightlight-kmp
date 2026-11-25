package com.chencohensolutions.nightlight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NightLightTest {

    @Test
    fun candleIsWarm() {
        val c = ColorTemperature.toRgb(ColorTemperature.CANDLE_KELVIN)
        assertEquals(1f, c.r)
        assertEquals(0f, c.b)
        assertTrue(c.g in 0.4f..0.6f, "green was ${c.g}")
    }

    @Test
    fun warmWhiteHasSomeBlue() {
        assertTrue(ColorTemperature.toRgb(ColorTemperature.MAX_KELVIN).b > 0.5f)
    }

    @Test
    fun warmthRoundTrips() {
        for (k in listOf(1200f, 1900f, 2700f, 4000f)) {
            val back = ColorTemperature.warmthToKelvin(ColorTemperature.kelvinToWarmth(k))
            assertTrue(kotlin.math.abs(back - k) < 0.5f)
        }
    }

    @Test
    fun breathingStaysInRange() {
        var t = 0.0
        while (t < 300.0) {
            for (b in Breathing.entries) {
                val i = FlameModel.intensity(b, t)
                assertTrue(i in FlameModel.MIN_INTENSITY..1f, "intensity $i at $t")
            }
            t += 0.016
        }
    }

    @Test
    fun breathIsAsymmetricAndSmooth() {
        assertEquals(0f, FlameModel.breath(0.0), 1e-6f)
        assertEquals(1f, FlameModel.breath(0.4), 1e-5f)
        assertEquals(0f, FlameModel.breath(1.0), 1e-6f)
        // Halfway up the inhale and halfway down the exhale.
        assertEquals(0.5f, FlameModel.breath(0.2), 1e-5f)
        assertEquals(0.5f, FlameModel.breath(0.7), 1e-5f)
        var p = 0.0
        while (p < 2.0) {
            val step = kotlin.math.abs(FlameModel.breath(p + 0.001) - FlameModel.breath(p))
            assertTrue(step < 0.006f, "breath jumps by $step at $p")
            p += 0.001
        }
    }

    @Test
    fun flickerAndGustsAreSmoothAndBounded() {
        var t = 0.0
        var sawGust = false
        while (t < 120.0) {
            val f = FlameModel.flicker(t)
            assertTrue(f in -1f..1f, "flicker $f")
            assertTrue(kotlin.math.abs(FlameModel.flicker(t + 0.008) - f) < 0.08f, "flicker jumps at $t")
            val g = FlameModel.gust(t)
            assertTrue(g in 0f..1f, "gust $g")
            assertTrue(kotlin.math.abs(FlameModel.gust(t + 0.008) - g) < 0.06f, "gust jumps at $t")
            if (g > 0.4f) sawGust = true
            t += 0.008
        }
        assertTrue(sawGust, "no gust in two minutes")
    }

    @Test
    fun dimmerFlameIsRedder() {
        val s = NightLightSettings(blueFilter = 0f, brightness = 1f)
        val bright = FlameModel.frame(s, 0.98f, 0.0, 1f)
        val dim = FlameModel.frame(s, 0.78f, 0.0, 1f)
        val steady = FlameModel.frame(s.copy(breathing = Breathing.Off), 0.0)
        assertTrue(dim.core.g / dim.core.r < bright.core.g / bright.core.r - 0.02f)
        // Steady mode at full brightness shows exactly the chosen color temperature.
        val expected = ColorTemperature.toRgb(s.kelvin) * FlameModel.STEADY_INTENSITY
        assertEquals(expected.g, steady.core.g, 1e-4f)
    }

    @Test
    fun haloIsRedderAndDarkerThanCore() {
        val f = FlameModel.frame(NightLightSettings(), 3.0)
        assertTrue(f.edge.r < f.core.r)
        assertTrue(f.edge.g / f.edge.r < f.core.g / f.core.r)
    }

    @Test
    fun falloffIsMonotonicFromCoreToEdge() {
        assertEquals(1f, FlameModel.falloff(0f), 1e-6f)
        assertEquals(0f, FlameModel.falloff(1f), 1e-6f)
        assertEquals(0f, FlameModel.falloff(3f), 1e-6f)
        var prev = 1f
        for (i in 1..100) {
            val v = FlameModel.falloff(i / 100f)
            assertTrue(v <= prev, "falloff rises at $i")
            prev = v
        }
        // Flat top: no pointed peak at the center.
        assertTrue(FlameModel.falloff(0.05f) > 0.98f)
        val f = FlameModel.frame(NightLightSettings(), 0.0)
        assertEquals(f.core, f.colorAt(0f))
        assertEquals(f.edge, f.colorAt(1f))
    }

    @Test
    fun glowSwaysOnlyWhenAnimated() {
        val steady = FlameModel.frame(NightLightSettings(breathing = Breathing.Off), 12.3)
        assertEquals(FlameModel.CENTER_X, steady.centerX)
        assertEquals(FlameModel.CENTER_Y, steady.centerY)
        var moved = false
        var t = 0.0
        while (t < 60.0) {
            val f = FlameModel.frame(NightLightSettings(), t)
            assertTrue(kotlin.math.abs(f.centerX - FlameModel.CENTER_X) <= 0.031f)
            assertTrue(kotlin.math.abs(f.centerY - FlameModel.CENTER_Y) <= 0.021f)
            assertTrue(f.radius in 0.7f..0.78f, "radius ${f.radius}")
            if (kotlin.math.abs(f.centerX - FlameModel.CENTER_X) > 0.003f) moved = true
            t += 0.1
        }
        assertTrue(moved)
    }

    @Test
    fun dimmerIsWarmerAndSmaller() {
        val off = Breathing.Off
        val full = FlameModel.frame(NightLightSettings(brightness = 1f, breathing = off, blueFilter = 0f), 0.0)
        val low = FlameModel.frame(NightLightSettings(brightness = 0f, breathing = off, blueFilter = 0f), 0.0)
        assertTrue(low.core.g / low.core.r < full.core.g / full.core.r)
        assertTrue(low.radius < full.radius)
        assertEquals(Rgb(0f, 0f, 0f), low.edge)
        assertEquals(0f, Brightness.kelvinShift(1f), 1e-6f)
    }

    @Test
    fun animatorKeepsBreathContinuousAcrossModeChanges() {
        val a = FlameAnimator()
        val dt = 1.0 / 120
        var settings = NightLightSettings(breathing = Breathing.Slow)
        var prev = a.step(0.0, settings).intensity
        val modes = listOf(Breathing.Lively, Breathing.Off, Breathing.Calm, Breathing.Slow, Breathing.Off)
        for (mode in modes) {
            repeat(700) {
                val i = a.step(dt, settings).intensity
                assertTrue(kotlin.math.abs(i - prev) < 0.01f, "jump of ${i - prev} in ${settings.breathing}")
                assertTrue(i in FlameModel.MIN_INTENSITY..1f)
                prev = i
            }
            settings = settings.copy(breathing = mode)
        }
        repeat(700) { prev = a.step(dt, settings).intensity }
        assertEquals(FlameModel.STEADY_INTENSITY, prev)
        assertTrue(a.isSettled)
    }

    @Test
    fun animatorSettlesInSteadyAndWakesUp() {
        val a = FlameAnimator()
        val steady = NightLightSettings(breathing = Breathing.Off)
        assertEquals(FlameModel.STEADY_INTENSITY, a.step(0.0, steady).intensity)
        assertTrue(a.isSettled)
        // Steady frames still follow the settings.
        assertTrue(a.step(0.0, steady.copy(brightness = 0.1f)).core.r < a.step(0.0, steady).core.r)
        a.step(0.016, steady.copy(breathing = Breathing.Calm))
        assertTrue(!a.isSettled)
        // A huge gap (app was in the background) must not blow up or leap.
        val i = a.step(3600.0, steady.copy(breathing = Breathing.Calm)).intensity
        assertTrue(i in FlameModel.MIN_INTENSITY..1f)
    }

    @Test
    fun framePacerLimitsTo30Fps() {
        val vsync = 1_000_000_000L / 120
        val limited = FramePacer(FramePacer.BATTERY_SAVER_FPS)
        val full = FramePacer()
        var rendered = 0
        var renderedFull = 0
        for (n in 0 until 120) {
            if (limited.shouldRender(n * vsync)) rendered++
            if (full.shouldRender(n * vsync)) renderedFull++
        }
        assertEquals(30, rendered)
        assertEquals(120, renderedFull)
        // On a 30 Hz panel every frame is kept.
        val slow = FramePacer(FramePacer.BATTERY_SAVER_FPS)
        assertTrue((0 until 30).all { slow.shouldRender(it * (1_000_000_000L / 30)) })
    }

    @Test
    fun brightnessCurve() {
        assertEquals(0.01f, Brightness.screen(0f), 1e-6f)
        assertEquals(1f, Brightness.screen(1f), 1e-6f)
        assertEquals(1f, Brightness.pixelGain(0.5f))
        assertEquals(0.4f, Brightness.pixelGain(0f), 1e-6f)
        assertEquals(1f, Brightness.glowScale(0.3f))
        assertTrue(Brightness.glowScale(0f) < 0.7f)
    }

    @Test
    fun blueFilterRemovesBlueAndWarmsGreen() {
        val white = ColorTemperature.toRgb(ColorTemperature.MAX_KELVIN)
        val off = BlueLightFilter.apply(white, 0f)
        val full = BlueLightFilter.apply(white, 1f)
        assertEquals(white, off)
        assertEquals(0f, full.b)
        assertEquals(white.r, full.r)
        assertTrue(full.g < white.g)
    }

    @Test
    fun filteredFrameIsWarmerThanUnfiltered() {
        val plain = FlameModel.frame(NightLightSettings(blueFilter = 0f, breathing = Breathing.Off), 0.0)
        val filtered = FlameModel.frame(NightLightSettings(blueFilter = 1f, breathing = Breathing.Off), 0.0)
        assertTrue(filtered.core.g / filtered.core.r < plain.core.g / plain.core.r)
    }

    @Test
    fun controllerClampsAndPersists() {
        val store = object : SettingsStore {
            var saved: NightLightSettings? = null
            override fun load() = null
            override fun save(settings: NightLightSettings) { saved = settings }
        }
        val c = NightLightController(store)
        c.nudgeBrightness(5f)
        assertEquals(1f, c.settings.value.brightness)
        c.setWarmth(1f)
        assertEquals(ColorTemperature.MIN_KELVIN, c.settings.value.kelvin)
        assertEquals(c.settings.value, store.saved)
        c.setLimitTo30Hz(true)
        assertEquals(true, store.saved?.limitTo30Hz)
        c.setBreathing(Breathing.Lively)
        c.resetToCandle()
        assertEquals(true, c.settings.value.limitTo30Hz)
        assertEquals(ColorTemperature.CANDLE_KELVIN, c.settings.value.kelvin)
    }
}
