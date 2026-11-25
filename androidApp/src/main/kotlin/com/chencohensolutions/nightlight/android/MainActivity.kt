package com.chencohensolutions.nightlight.android

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import com.chencohensolutions.nightlight.Brightness
import com.chencohensolutions.nightlight.FlameModel
import com.chencohensolutions.nightlight.NightLightController
import com.chencohensolutions.nightlight.SharedPrefsSettingsStore

class NightLightViewModel(app: Application) : AndroidViewModel(app) {
    val controller = NightLightController(SharedPrefsSettingsStore(app))
    val torch = TorchController(app)
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

            // Breathing: hold the panel at its top refresh rate (or 30 Hz when limited).
            // Steady: nothing moves, so let the system pick.
            val animated = FlameModel.isAnimated(settings)
            LaunchedEffect(animated, settings.limitTo30Hz) {
                applyRefreshRate(
                    when {
                        !animated -> RefreshRate.System
                        settings.limitTo30Hz -> RefreshRate.Low
                        else -> RefreshRate.Max
                    },
                )
            }

            NightLightTheme {
                NightLightScreen(viewModel.controller, viewModel.torch)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.torch.start()
    }

    override fun onStop() {
        viewModel.torch.stop()
        super.onStop()
    }

    override fun onDestroy() {
        // Leaving the app keeps the flashlight on; closing the app turns off what it turned on.
        if (isFinishing) viewModel.torch.turnOffIfTurnedOnByApp()
        super.onDestroy()
    }

    private enum class RefreshRate { System, Low, Max }

    private fun applyRefreshRate(rate: RefreshRate) {
        val best = if (rate == RefreshRate.Max) highestRefreshMode() else null
        window.attributes = window.attributes.apply {
            when (rate) {
                RefreshRate.Max -> {
                    // Pin the fastest mode at the current resolution, so an adaptive (LTPO)
                    // panel doesn't drop to 60 Hz for content it considers slow.
                    preferredDisplayModeId = best?.modeId ?: 0
                    preferredRefreshRate = best?.refreshRate ?: 0f
                }
                RefreshRate.Low -> {
                    preferredDisplayModeId = 0
                    preferredRefreshRate = LOW_REFRESH_RATE
                }
                RefreshRate.System -> {
                    preferredDisplayModeId = 0
                    preferredRefreshRate = 0f
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Android 15+ may lower the frame rate of slow animations to save power.
            // At full rate we opt out; otherwise the default (balanced) behavior is what we want.
            window.setFrameRatePowerSavingsBalanced(rate != RefreshRate.Max)
        }
    }

    private fun highestRefreshMode(): Display.Mode? = try {
        @Suppress("DEPRECATION")
        val screen: Display? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) this.display else windowManager.defaultDisplay
        val current = screen?.mode
        screen?.supportedModes
            ?.filter {
                current == null ||
                    (it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight)
            }
            ?.maxByOrNull { it.refreshRate }
    } catch (e: Exception) {
        null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private companion object {
        const val LOW_REFRESH_RATE = 30f
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
