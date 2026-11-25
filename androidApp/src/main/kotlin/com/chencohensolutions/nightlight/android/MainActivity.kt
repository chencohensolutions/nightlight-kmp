package com.chencohensolutions.nightlight.android

import android.app.Application
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import com.chencohensolutions.nightlight.Brightness
import com.chencohensolutions.nightlight.NightLightController
import com.chencohensolutions.nightlight.SharedPrefsSettingsStore

class NightLightViewModel(app: Application) : AndroidViewModel(app) {
    val controller = NightLightController(SharedPrefsSettingsStore(app))
}

class MainActivity : ComponentActivity() {

    private val viewModel: NightLightViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The whole point of the app: never let the screen sleep while it's open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        setContent {
            val settings by viewModel.controller.settings.collectAsState()

            // Window-level override: affects only this app, restored automatically on leave.
            LaunchedEffect(settings.brightness) {
                window.attributes = window.attributes.apply {
                    screenBrightness = Brightness.screen(settings.brightness)
                }
            }

            NightLightTheme {
                NightLightScreen(
                    controller = viewModel.controller,
                    systemFilterOn = systemFilterOn.value,
                    onTurnOffSystemFilter = { SystemBlueFilter.openSettings(this) },
                )
            }
        }
    }

    // --- System blue light filter (Eye comfort shield) detection ---

    private val systemFilterOn = mutableStateOf(false)

    private val systemFilterObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshSystemFilter()
    }

    override fun onStart() {
        super.onStart()
        SystemBlueFilter.uris().forEach { uri ->
            runCatching { contentResolver.registerContentObserver(uri, false, systemFilterObserver) }
        }
        refreshSystemFilter()
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(systemFilterObserver)
        super.onStop()
    }

    private fun refreshSystemFilter() {
        systemFilterOn.value = SystemBlueFilter.isOn(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
