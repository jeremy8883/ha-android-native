package io.homeassistant.companion.android.dashboard.ui.moreinfo

/** What the favourites row reports. */
internal data class FavoritesRowCallbacks(
    val onApply: (Int) -> Unit,
    val onEdit: (Int) -> Unit,
    val onDelete: (Int) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onAdd: () -> Unit,
    val onStartEditing: () -> Unit,
    val onDone: () -> Unit,
)
