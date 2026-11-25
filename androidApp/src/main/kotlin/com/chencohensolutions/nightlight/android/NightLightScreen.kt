package com.chencohensolutions.nightlight.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chencohensolutions.nightlight.Breathing
import com.chencohensolutions.nightlight.FlameModel
import com.chencohensolutions.nightlight.NightLightController
import com.chencohensolutions.nightlight.NightLightSettings
import com.chencohensolutions.nightlight.Rgb
import com.chencohensolutions.nightlight.blueFilterLabel
import com.chencohensolutions.nightlight.brightnessLabel
import com.chencohensolutions.nightlight.warmth
import com.chencohensolutions.nightlight.warmthLabel
import kotlinx.coroutines.delay

private val Amber = Color(0xFFFFB35C)
private val Ink = Color(0xFF1A0A00)

@Composable
fun NightLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Amber, onPrimary = Ink, background = Ink),
        content = content,
    )
}

private fun Rgb.toColor() = Color(r, g, b)

@Composable
fun NightLightScreen(
    controller: NightLightController,
) {
    val settings by controller.settings.collectAsState()

    // Animation clock. Only read inside drawBehind, so frames redraw without recomposing.
    var time by remember { mutableDoubleStateOf(0.0) }
    val animated = FlameModel.isAnimated(settings)
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect // steady mode: no frame loop, saves battery
        val offset = time
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> time = offset + (now - start) / 1_000_000_000.0 }
        }
    }

    var controlsVisible by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, interaction) {
        if (controlsVisible) {
            delay(5_000)
            controlsVisible = false
        }
    }

    var showHint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3_500)
        showHint = false
    }

    var readout by remember { mutableStateOf<String?>(null) }
    var readoutTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(readoutTick) {
        delay(900)
        readout = null
    }
    fun showReadout(text: String) {
        readout = text
        readoutTick++
    }

    val edges = rememberGestureEdges()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val frame = FlameModel.frame(settings, time)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(frame.core.toColor(), frame.edge.toColor()),
                        center = Offset(size.width / 2f, size.height * 0.6f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        showHint = false
                    },
                    onDoubleTap = {
                        controller.resetToCandle()
                        showReadout(controller.settings.value.warmthLabel)
                    },
                )
            }
            // Swipes ignore touches that start in the OS gesture zones (back on the
            // sides, home/shade at bottom/top), and revert if the OS takes the touch over.
            .pointerInput(edges) {
                var active = false
                var before: NightLightSettings? = null
                detectVerticalDragGestures(
                    onDragStart = { start ->
                        active = start.y > edges.top && start.y < size.height - edges.bottom &&
                            start.x > edges.left && start.x < size.width - edges.right
                        before = controller.settings.value
                    },
                    onDragCancel = { if (active) before?.let(controller::restore); active = false },
                    onDragEnd = { active = false },
                ) { change, dy ->
                    if (!active) return@detectVerticalDragGestures
                    change.consume()
                    controller.nudgeBrightness(-dy / size.height)
                    interaction++
                    if (!controlsVisible) showReadout("Brightness ${controller.settings.value.brightnessLabel}")
                }
            }
            .pointerInput(edges) {
                var active = false
                var before: NightLightSettings? = null
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        active = start.x > edges.left && start.x < size.width - edges.right &&
                            start.y > edges.top && start.y < size.height - edges.bottom
                        before = controller.settings.value
                    },
                    onDragCancel = { if (active) before?.let(controller::restore); active = false },
                    onDragEnd = { active = false },
                ) { change, dx ->
                    if (!active) return@detectHorizontalDragGestures
                    change.consume()
                    controller.nudgeWarmth(dx / size.width)
                    interaction++
                    if (!controlsVisible) showReadout(controller.settings.value.warmthLabel)
                }
            },
    ) {
        readout?.let {
            Text(
                text = it,
                color = Ink.copy(alpha = 0.75f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = showHint && !controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Tap for controls · swipe ↕ brightness · ↔ warmth",
                color = Ink.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ControlsPanel(
                settings = settings,
                onBrightness = { controller.setBrightness(it); interaction++ },
                onWarmth = { controller.setWarmth(it); interaction++ },
                onBlueFilter = { controller.setBlueFilter(it); interaction++ },
                onBreathing = { controller.setBreathing(it); interaction++ },
            )
        }
    }
}

/** Edge zones (px) owned by OS gestures. */
private data class GestureEdges(val left: Float, val top: Float, val right: Float, val bottom: Float)

@Composable
private fun rememberGestureEdges(): GestureEdges {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val insets = WindowInsets.systemGestures
    // Insets can report 0 in immersive mode, so keep a minimum that covers
    // Samsung's back-gesture zone at high sensitivity.
    val minSide = with(density) { 40.dp.toPx() }
    val minTopBottom = with(density) { 48.dp.toPx() }
    val left = maxOf(insets.getLeft(density, dir).toFloat(), minSide)
    val right = maxOf(insets.getRight(density, dir).toFloat(), minSide)
    val top = maxOf(insets.getTop(density).toFloat(), minTopBottom)
    val bottom = maxOf(insets.getBottom(density).toFloat(), minTopBottom)
    return remember(left, top, right, bottom) { GestureEdges(left, top, right, bottom) }
}

@Composable
private fun ControlsPanel(
    settings: NightLightSettings,
    onBrightness: (Float) -> Unit,
    onWarmth: (Float) -> Unit,
    onBlueFilter: (Float) -> Unit,
    onBreathing: (Breathing) -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { } } // taps on the panel don't close it
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        LabeledSlider("Brightness", settings.brightnessLabel, settings.brightness, onBrightness)
        Spacer(Modifier.height(4.dp))
        LabeledSlider("Warmth", settings.warmthLabel, settings.warmth, onWarmth)
        Spacer(Modifier.height(4.dp))
        LabeledSlider("Blue light filter", settings.blueFilterLabel, settings.blueFilter, onBlueFilter)
        Spacer(Modifier.height(12.dp))
        Text("Breathing", color = Amber, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Breathing.entries.forEach { mode ->
                ModeChip(
                    label = mode.label,
                    selected = settings.breathing == mode,
                    onClick = { onBreathing(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(title: String, value: String, fraction: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = Amber, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = Amber.copy(alpha = 0.7f), fontSize = 13.sp)
    }
    Slider(
        value = fraction,
        onValueChange = onChange,
        colors = SliderDefaults.colors(
            thumbColor = Amber,
            activeTrackColor = Amber,
            inactiveTrackColor = Amber.copy(alpha = 0.25f),
        ),
    )
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(if (selected) Amber else Color.Transparent)
            .border(1.dp, Amber.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(label, color = if (selected) Ink else Amber, fontSize = 13.sp, maxLines = 1)
    }
}
