package io.homeassistant.companion.android.dashboard.derive

/**
 * Display-ready content of the tile-style info cards (home summary, repairs, updates, discovered devices),
 * which upstream all draw with `ha-tile-container`.
 *
 * @property color the colour name (`amber`, `deep-orange`, `warning`, ...), as upstream sets `--tile-color`
 * @property secondary the summary line ("3 on", "2 updates"); empty when there is nothing to say
 * @property loading whether [secondary] is still being loaded
 * @property failed whether [secondary] couldn't be loaded
 */
data class InfoTileModel(
    val label: String,
    val icon: String,
    val color: String,
    val secondary: String,
    val loading: Boolean,
    val vertical: Boolean,
    val failed: Boolean = false,
)
