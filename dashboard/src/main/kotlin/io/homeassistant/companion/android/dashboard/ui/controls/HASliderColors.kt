package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/** The theme's colours for a plain Material slider (upstream's `ha-slider`). */
@Composable
internal fun haSliderColors(): SliderColors = LocalHAColorScheme.current.let { colors ->
    SliderDefaults.colors(
        thumbColor = colors.colorFillPrimaryLoudResting,
        activeTrackColor = colors.colorFillPrimaryLoudResting,
        inactiveTrackColor = colors.colorFillDisabledLoudResting,
        disabledThumbColor = colors.colorFillDisabledLoudResting,
        disabledActiveTrackColor = colors.colorFillDisabledLoudResting,
        disabledInactiveTrackColor = colors.colorFillDisabledQuietResting,
    )
}
