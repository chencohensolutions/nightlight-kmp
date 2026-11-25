package com.chencohensolutions.nightlight.android

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import com.chencohensolutions.nightlight.FlameFrame
import com.chencohensolutions.nightlight.FlameModel
import kotlin.math.exp
import kotlin.math.max

/** Paints one [FlameFrame] over the whole canvas. */
internal interface GlowPainter {
    fun draw(canvas: Canvas, width: Float, height: Float, frame: FlameFrame)
}

internal fun createGlowPainter(): GlowPainter =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ShaderGlowPainter() else GradientGlowPainter()

/**
 * Android 13+: the glow is computed per pixel, with noise of less than one color step
 * added before the color is rounded to 8 bits. That turns the wide bands a dark gradient
 * would show into grain too fine to see.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ShaderGlowPainter : GlowPainter {
    private val shader = RuntimeShader(GLOW_SHADER)
    private val paint = Paint()

    init {
        val sigma = FlameModel.FALLOFF_SIGMA
        val k = 1f / (2f * sigma * sigma)
        shader.setFloatUniform("uFalloffK", k)
        shader.setFloatUniform("uFalloffEnd", exp(-k))
        paint.shader = shader
    }

    override fun draw(canvas: Canvas, width: Float, height: Float, frame: FlameFrame) {
        shader.setFloatUniform("uCenter", width * frame.centerX, height * frame.centerY)
        shader.setFloatUniform("uRadius", max(1f, max(width, height) * frame.radius))
        shader.setFloatUniform("uCore", frame.core.r, frame.core.g, frame.core.b)
        shader.setFloatUniform("uEdge", frame.edge.r, frame.edge.g, frame.edge.b)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}

/** Before Android 13: the same profile as a multi-stop radial gradient, dithered by the platform. */
private class GradientGlowPainter : GlowPainter {
    private val paint = Paint().apply { isDither = true }
    private val colors = IntArray(STOPS)
    private val positions = FloatArray(STOPS) { it / (STOPS - 1f) }

    override fun draw(canvas: Canvas, width: Float, height: Float, frame: FlameFrame) {
        for (i in 0 until STOPS) {
            colors[i] = frame.colorAt(positions[i]).toArgb().toInt()
        }
        paint.shader = RadialGradient(
            width * frame.centerX,
            height * frame.centerY,
            max(1f, max(width, height) * frame.radius),
            colors,
            positions,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private companion object {
        const val STOPS = 17
    }
}

// Must match FlameModel.falloff(). The last lines are the dither: interleaved gradient
// noise, offset per channel, at most half a color step either way (so black stays black).
private const val GLOW_SHADER = """
uniform float2 uCenter;
uniform float uRadius;
uniform float3 uCore;
uniform float3 uEdge;
uniform float uFalloffK;
uniform float uFalloffEnd;

half4 main(float2 p) {
    float x = min(distance(p, uCenter) / uRadius, 1.0);
    float m = clamp((exp(-x * x * uFalloffK) - uFalloffEnd) / (1.0 - uFalloffEnd), 0.0, 1.0);
    float3 c = mix(uEdge, uCore, m);
    float n = fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
    float3 d = fract(float3(n, n + 0.3333, n + 0.6667)) - 0.5;
    c = clamp(c + d / 255.0, 0.0, 1.0);
    return half4(c.r, c.g, c.b, 1.0);
}
"""
