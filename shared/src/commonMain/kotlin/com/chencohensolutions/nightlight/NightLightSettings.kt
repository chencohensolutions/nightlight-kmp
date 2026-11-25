package com.chencohensolutions.nightlight

enum class Breathing(val label: String, val periodSeconds: Double) {
    Off("Steady", 0.0),
    /** 6 breaths a minute: the pace of slow, relaxed breathing. */
    Slow("Slow", 10.0),
    Calm("Calm", 7.5),
    Lively("Lively", 4.5),
}

data class NightLightSettings(
    /** 0 = dimmest, 1 = brightest. */
    val brightness: Float = 0.5f,
    val kelvin: Float = ColorTemperature.CANDLE_KELVIN,
    val breathing: Breathing = Breathing.Calm,
    /** In-app blue light reduction, 0 = off, 1 = strongest. */
    val blueFilter: Float = 0.6f,
    /** Render at ~30 fps instead of the display's full refresh rate, to save battery. */
    val limitTo30Hz: Boolean = false,
) {
    fun normalized() = copy(
        brightness = brightness.coerceIn(0f, 1f),
        blueFilter = blueFilter.coerceIn(0f, 1f),
        kelvin = kelvin.coerceIn(ColorTemperature.MIN_KELVIN, ColorTemperature.MAX_KELVIN),
    )
}

/** Platform persistence (SharedPreferences on Android, NSUserDefaults on iOS later). */
interface SettingsStore {
    fun load(): NightLightSettings?
    fun save(settings: NightLightSettings)
}

object InMemorySettingsStore : SettingsStore {
    private var value: NightLightSettings? = null
    override fun load() = value
    override fun save(settings: NightLightSettings) { value = settings }
}
