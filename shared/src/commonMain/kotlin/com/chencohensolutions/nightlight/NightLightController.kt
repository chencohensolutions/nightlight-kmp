package com.chencohensolutions.nightlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single source of truth for the UI. Platform code only renders and forwards input. */
class NightLightController(private val store: SettingsStore) {

    private val _settings = MutableStateFlow(store.load()?.normalized() ?: NightLightSettings())
    val settings: StateFlow<NightLightSettings> = _settings.asStateFlow()

    fun setBrightness(value: Float) = update { copy(brightness = value) }

    /** Swipe support: delta in slider units (full screen height ~ 1.0). */
    fun nudgeBrightness(delta: Float) = update { copy(brightness = brightness + delta) }

    /** 0 = coolest, 1 = warmest. */
    fun setWarmth(value: Float) = update { copy(kelvin = ColorTemperature.warmthToKelvin(value)) }

    fun nudgeWarmth(delta: Float) = update {
        copy(kelvin = ColorTemperature.warmthToKelvin(ColorTemperature.kelvinToWarmth(kelvin) + delta))
    }

    fun setBreathing(value: Breathing) = update { copy(breathing = value) }

    fun setBlueFilter(value: Float) = update { copy(blueFilter = value) }

    fun setLimitTo30Hz(value: Boolean) = update { copy(limitTo30Hz = value) }

    /** Undo an in-progress gesture (e.g. the OS took the touch for its back gesture). */
    fun restore(settings: NightLightSettings) = update { settings }

    /** Back to candle color; keeps brightness, blue filter and the frame rate limit. */
    fun resetToCandle() = update {
        NightLightSettings(brightness = brightness, blueFilter = blueFilter, limitTo30Hz = limitTo30Hz)
    }

    private inline fun update(change: NightLightSettings.() -> NightLightSettings) {
        val current = _settings.value
        val next = current.change().normalized()
        if (next != current) {
            _settings.value = next
            store.save(next)
        }
    }
}

/** Read-only view model state for the controls panel. */
val NightLightSettings.warmth: Float get() = ColorTemperature.kelvinToWarmth(kelvin)
val NightLightSettings.warmthLabel: String get() = "${ColorTemperature.label(kelvin)} · ${kelvin.toInt()}K"
val NightLightSettings.brightnessLabel: String get() = "${(brightness * 100).toInt()}%"
val NightLightSettings.blueFilterLabel: String get() =
    if (blueFilter < 0.01f) "Off" else "${(blueFilter * 100).toInt()}%"
