package io.homeassistant.companion.android.dashboard.ui.controls

/** The light's channels that tint the wheel: its colour brightness and whites, each 0–255, `null` when absent. */
internal data class WheelChannels(
    val colorBrightness: Double?,
    val white: Double?,
    val coldWhite: Double?,
    val warmWhite: Double?,
    val minKelvin: Double?,
    val maxKelvin: Double?,
)
