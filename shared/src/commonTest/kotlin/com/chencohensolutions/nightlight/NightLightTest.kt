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
        while (t < 60.0) {
            for (b in Breathing.entries) {
                assertTrue(FlameModel.intensity(b, t) in 0.7f..1f)
            }
            t += 0.016
        }
    }

    @Test
    fun brightnessCurve() {
        assertEquals(0.01f, Brightness.screen(0f), 1e-6f)
        assertEquals(1f, Brightness.screen(1f), 1e-6f)
        assertEquals(1f, Brightness.pixelGain(0.5f))
        assertTrue(Brightness.pixelGain(0f) < 0.3f)
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
    }
}
