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
        )
    }

    override fun save(settings: NightLightSettings) {
        prefs.edit()
            .putFloat(KEY_BRIGHTNESS, settings.brightness)
            .putFloat(KEY_KELVIN, settings.kelvin)
            .putString(KEY_BREATHING, settings.breathing.name)
            .apply()
    }

    private companion object {
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_KELVIN = "kelvin"
        const val KEY_BREATHING = "breathing"
    }
}
