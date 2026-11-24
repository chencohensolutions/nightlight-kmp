package com.chencohensolutions.nightlight

/**
 * One slider drives two things:
 *  - the hardware backlight (window/screen brightness), and
 *  - the pixel color gain, so the bottom of the slider goes darker than the
 *    backlight's minimum alone allows (important in a dark bedroom).
 */
object Brightness {
    private const val MIN_SCREEN = 0.01f
    private const val PIXEL_DIM_ZONE = 0.3f
    private const val MIN_PIXEL_GAIN = 0.2f

    /** Value for WindowManager.LayoutParams.screenBrightness (0..1). */
    fun screen(brightness: Float): Float {
        val b = brightness.coerceIn(0f, 1f)
        // Squared so the low end, where eyes are most sensitive, gets more slider travel.
        return MIN_SCREEN + (1f - MIN_SCREEN) * b * b
    }

    /** Multiplier applied to the rendered color (0..1). */
    fun pixelGain(brightness: Float): Float {
        val b = brightness.coerceIn(0f, 1f)
        if (b >= PIXEL_DIM_ZONE) return 1f
        return MIN_PIXEL_GAIN + (1f - MIN_PIXEL_GAIN) * (b / PIXEL_DIM_ZONE)
    }
}
