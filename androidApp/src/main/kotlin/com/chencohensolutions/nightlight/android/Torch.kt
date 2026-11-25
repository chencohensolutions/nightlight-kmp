package com.chencohensolutions.nightlight.android

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone's camera flash as a flashlight. Uses the torch API, which needs no camera
 * permission. All methods are meant to be called on the main thread.
 */
class TorchController(context: Context) {
    private val manager =
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val cameraId: String? = findFlashCamera()
    private val handler = Handler(Looper.getMainLooper())

    private val _isOn = MutableStateFlow(false)
    /** Follows the real torch state, including changes made from Quick Settings. */
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    /** False on devices without a flash: hide the button. */
    val isAvailable: Boolean get() = manager != null && cameraId != null

    private var turnedOnByApp = false
    private var registered = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id != cameraId) return
            _isOn.value = enabled
            if (!enabled) turnedOnByApp = false
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id != cameraId) return
            _isOn.value = false
            turnedOnByApp = false
        }
    }

    /** Start following the torch state (call from onStart). */
    fun start() {
        val m = manager ?: return
        if (cameraId == null || registered) return
        try {
            m.registerTorchCallback(callback, handler)
            registered = true
        } catch (e: Exception) {
            // Leave the button in its last known state.
        }
    }

    /** Stop following the torch state (call from onStop). The torch itself is left as it is. */
    fun stop() {
        val m = manager ?: return
        if (!registered) return
        registered = false
        try {
            m.unregisterTorchCallback(callback)
        } catch (e: Exception) {
            // Nothing to undo.
        }
    }

    /** Switch the flashlight. Returns false if it could not be switched (e.g. the camera is in use). */
    fun toggle(): Boolean = set(!_isOn.value)

    /** Turn the flashlight off, but only if this app was the one that turned it on. */
    fun turnOffIfTurnedOnByApp() {
        if (turnedOnByApp) set(false)
    }

    private fun set(on: Boolean): Boolean {
        val m = manager ?: return false
        val id = cameraId ?: return false
        return try {
            m.setTorchMode(id, on)
            turnedOnByApp = on
            // The callback confirms this; set it now too so the button reacts at once.
            _isOn.value = on
            true
        } catch (e: Exception) {
            // CameraAccessException (camera in use or disabled) or IllegalArgumentException.
            false
        }
    }

    private fun findFlashCamera(): String? {
        val m = manager ?: return null
        return try {
            m.cameraIdList.firstOrNull { id ->
                val c = m.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) {
            null
        }
    }
}
