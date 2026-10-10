package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/** How a [ControlSlider] looks: its [thickness] and [cornerRadius], and the colours of its bar and track. */
internal data class ControlSliderStyle(
    val thickness: Dp,
    val cornerRadius: Dp,
    val color: Color,
    val background: Brush,
    val backgroundAlpha: Float,
    val tooltipFontSize: TextUnit,
)
