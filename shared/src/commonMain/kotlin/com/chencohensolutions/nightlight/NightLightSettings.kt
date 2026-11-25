package com.chencohensolutions.nightlight

enum class Breathing(val label: String, val periodSeconds: Double) {
    Off("Steady", 0.0),
    Slow("Slow", 7.0),
    Calm("Calm", 4.5),
    Lively("Lively", 2.5),
}

data class NightLightSettings(
    /** 0 = dimmest, 1 = brightest. */
    val brightness: Float = 0.5f,
    val kelvin: Float = ColorTemperature.CANDLE_KELVIN,
    val breathing: Breathing = Breathing.Calm,
    /** In-app blue light reduction, 0 = off, 1 = strongest. */
    val blueFilter: Float = 0.6f,
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
