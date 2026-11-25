package com.chencohensolutions.nightlight

/**
 * One slider drives:
 *  - the hardware backlight (window/screen brightness),
 *  - the pixel color gain and the size of the glow, so the bottom of the slider goes
 *    darker than the backlight's minimum alone allows (important in a dark bedroom), and
 *  - a small shift toward warmer light as it dims.
 */
object Brightness {
    private const val MIN_SCREEN = 0.01f
    private const val PIXEL_DIM_ZONE = 0.3f
    // Not lower: with 8 bits per channel a dark full-screen gradient has too few levels
    // left and shows bands. The rest of the dimming comes from shrinking the glow.
    private const val MIN_PIXEL_GAIN = 0.4f
    private const val MIN_GLOW_SCALE = 0.6f
    /** Dim light looks natural when it is warmer, and it carries less green. */
    private const val DIM_KELVIN_SHIFT = -200f

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
        return MIN_PIXEL_GAIN + (1f - MIN_PIXEL_GAIN) * dimZone(b)
    }

    /**
     * Glow radius multiplier (0..1). At the bottom of the slider the glow shrinks, so fewer
     * pixels are lit: less light in the room and less power on an OLED screen.
     */
    fun glowScale(brightness: Float): Float =
        MIN_GLOW_SCALE + (1f - MIN_GLOW_SCALE) * dimZone(brightness)

    /**
     * Multiplier for the glow's outer color (0..1). It fades to true black at the bottom of
     * the slider, where OLED pixels switch off completely.
     */
    fun edgeLevel(brightness: Float): Float = dimZone(brightness)

    /** Kelvin offset for the brightness: 0 at full brightness, warmer as it dims. */
    fun kelvinShift(brightness: Float): Float =
        DIM_KELVIN_SHIFT * (1f - brightness.coerceIn(0f, 1f))

    /** 0 at the bottom of the slider, 1 from [PIXEL_DIM_ZONE] up. */
    private fun dimZone(brightness: Float): Float =
        (brightness.coerceIn(0f, 1f) / PIXEL_DIM_ZONE).coerceIn(0f, 1f)
}
