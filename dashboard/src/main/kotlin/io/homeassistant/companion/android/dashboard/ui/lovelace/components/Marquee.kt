package io.homeassistant.companion.android.dashboard.ui.lovelace.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx

/**
 * Port of `hui-marquee`: one line of [text], cut with an ellipsis; once active (tapped, or hovered with a mouse, as a
 * browser's touch emulation hovers it) and only when it overflows, it scrolls by in a loop, two copies apart by
 * 16px, a lap every 10s, and stops at the end of the lap once inactive again (tapped again, or the pointer gone).
 *
 * Upstream: `hui-marquee` (src/panels/lovelace/components/hui-marquee.ts)
 */
@Composable
internal fun Marquee(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf(false) }
    // `updated`: a new text stops the animation
    var animating by remember(text) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val textWidth = remember(text, style) { measurer.measure(text, style, softWrap = false, maxLines = 1).size.width }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .pointerInput(Unit) { detectTapGestures { active = !active } }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent().type) {
                            PointerEventType.Enter -> active = true
                            PointerEventType.Exit -> active = false
                        }
                    }
                }
            },
    ) {
        val overflowing = textWidth > constraints.maxWidth
        LaunchedEffect(active, overflowing) { if (active && overflowing) animating = true }
        if (animating) {
            MarqueeLoop(text, style, color, textWidth, active) { animating = false }
        } else {
            Text(text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The two copies scrolling a lap at a time while [active], then [onStopped].
 *
 * Upstream: part of `hui-marquee` (src/panels/lovelace/components/hui-marquee.ts)
 */
@Composable
private fun MarqueeLoop(
    text: String,
    style: TextStyle,
    color: Color,
    textWidth: Int,
    active: Boolean,
    onStopped: () -> Unit,
) {
    val currentActive by rememberUpdatedState(active)
    val lap = textWidth + with(LocalDensity.current) { GAP.toPx() }
    var offset by remember(text) { mutableFloatStateOf(0f) }
    LaunchedEffect(text) {
        // Timed on the frame clock, as a CSS animation, which the system's animation scale doesn't stop
        var lapStart = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            if (now - lapStart >= LAP_NANOS) {
                // `_onIteration`: each lap ends with a check whether to go on
                if (!currentActive) break
                lapStart += (now - lapStart) / LAP_NANOS * LAP_NANOS
            }
            offset = (now - lapStart).toFloat() / LAP_NANOS
        }
        onStopped()
    }
    Row(
        Modifier
            .wrapContentWidth(Alignment.Start, unbounded = true)
            .graphicsLayer { translationX = -offset * lap },
    ) {
        repeat(COPIES) {
            Text(
                text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(end = GAP),
            )
        }
    }
}

/** `animation: marquee 10s linear infinite`. */
private const val LAP_NANOS = 10_000_000_000L
private const val COPIES = 2

/** `:host([animating]) .marquee-inner span { padding-right: 16px }`. */
private val GAP = 16.cssPx
