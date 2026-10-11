package io.homeassistant.companion.android.dashboard.panels

import io.homeassistant.companion.android.dashboard.navigation.PanelInfo
import io.homeassistant.companion.android.dashboard.navigation.SidebarItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NativePanelsTest {

    private fun panel(path: String, component: String?) = PanelInfo(
        urlPath = path,
        componentName = component,
        title = path,
        icon = null,
        showInSidebar = true,
        defaultVisible = true,
        requireAdmin = false,
        hasConfig = false,
    )

    private val panels = listOf(
        panel("lovelace", "lovelace"),
        panel("media-browser", "media-browser"),
        panel("history", "history"),
        panel("music-assistant", "custom"),
        panel("energy", "energy"),
        panel("calendar", "calendar"),
        panel("todo", "todo"),
        panel("light", "light"),
    ).associateBy { it.urlPath }

    @Test
    fun `Given the sidebar when grouping it then native pages come first and the others apart, each in order`() {
        val items = panels.keys.map { SidebarItem(it, it, "mdi:$it") } + SidebarItem("gone", "gone", "mdi:help")

        val groups = items.groupedByNative(panels)

        assertEquals(listOf("lovelace", "history", "energy", "todo", "light"), groups.native.map { it.urlPath })
        assertEquals(listOf("media-browser", "music-assistant", "calendar", "gone"), groups.elsewhere.map { it.urlPath })
    }
}
