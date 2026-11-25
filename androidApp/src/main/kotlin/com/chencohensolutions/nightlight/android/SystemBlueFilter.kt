package com.chencohensolutions.nightlight.android

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Detects the system blue light filter (Samsung "Eye comfort shield", AOSP "Night Light").
 *
 * Third-party apps can't turn these off: there is no public API, and Samsung's
 * setting is private. So the app only *detects* it and sends the user one tap
 * away from the toggle, while the in-app BlueLightFilter does the warming instead.
 */
object SystemBlueFilter {
    /** Samsung One UI: Eye comfort shield / Blue light filter. */
    private const val SAMSUNG_KEY = "blue_light_filter"
    /** AOSP / Pixel: Night Light. */
    private const val AOSP_KEY = "night_display_activated"

    fun isOn(context: Context): Boolean {
        val cr = context.contentResolver
        return readSystem(cr, SAMSUNG_KEY) == 1 || readSecure(cr, AOSP_KEY) == 1
    }

    /** URIs to observe for live updates while the app is open. */
    fun uris(): List<Uri> = listOfNotNull(
        runCatching { Settings.System.getUriFor(SAMSUNG_KEY) }.getOrNull(),
        runCatching { Settings.Secure.getUriFor(AOSP_KEY) }.getOrNull(),
    )

    fun openSettings(context: Context) {
        val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        val candidates = buildList {
            // On One UI, Eye comfort shield sits at the top of Display settings.
            if (!isSamsung) add(Intent("android.settings.NIGHT_DISPLAY_SETTINGS"))
            add(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // try the next one
            }
        }
    }

    // Unknown or restricted keys throw; treat them as "off / can't tell".
    private fun readSystem(cr: ContentResolver, key: String): Int? =
        try { Settings.System.getInt(cr, key) } catch (_: Exception) { null }

    private fun readSecure(cr: ContentResolver, key: String): Int? =
        try { Settings.Secure.getInt(cr, key) } catch (_: Exception) { null }
}
