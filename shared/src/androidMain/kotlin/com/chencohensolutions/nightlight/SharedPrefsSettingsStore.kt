package com.chencohensolutions.nightlight

import android.content.Context

class SharedPrefsSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("nightlight", Context.MODE_PRIVATE)

    override fun load(): NightLightSettings? {
        if (!prefs.contains(KEY_BRIGHTNESS)) return null
        val defaults = NightLightSettings()
        return NightLightSettings(
            brightness = prefs.getFloat(KEY_BRIGHTNESS, defaults.brightness),
            kelvin = prefs.getFloat(KEY_KELVIN, defaults.kelvin),
            breathing = prefs.getString(KEY_BREATHING, null)
                ?.let { name -> Breathing.entries.firstOrNull { it.name == name } }
                ?: defaults.breathing,
            blueFilter = prefs.getFloat(KEY_BLUE_FILTER, defaults.blueFilter),
            limitTo30Hz = prefs.getBoolean(KEY_LIMIT_30HZ, defaults.limitTo30Hz),
        )
    }

    override fun save(settings: NightLightSettings) {
        prefs.edit()
            .putFloat(KEY_BRIGHTNESS, settings.brightness)
            .putFloat(KEY_KELVIN, settings.kelvin)
            .putString(KEY_BREATHING, settings.breathing.name)
            .putFloat(KEY_BLUE_FILTER, settings.blueFilter)
            .putBoolean(KEY_LIMIT_30HZ, settings.limitTo30Hz)
            .apply()
    }

    private companion object {
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_KELVIN = "kelvin"
        const val KEY_BREATHING = "breathing"
        const val KEY_BLUE_FILTER = "blue_filter"
        const val KEY_LIMIT_30HZ = "limit_30hz"
    }
}
