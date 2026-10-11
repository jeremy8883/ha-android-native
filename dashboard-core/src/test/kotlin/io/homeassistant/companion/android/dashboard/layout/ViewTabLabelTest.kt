package io.homeassistant.companion.android.dashboard.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ViewTabLabelTest {
    @Test
    fun `Given a view with an icon when labelling its tab then the icon alone`() {
        assertEquals(ViewTabLabel.IconOnly("mdi:home", "Home"), viewTabLabel("Home", "mdi:home", false))
        assertEquals(ViewTabLabel.IconOnly("mdi:home", null), viewTabLabel(null, "mdi:home", true))
    }

    @Test
    fun `Given show_icon_and_title with an icon and title when labelling its tab then both`() {
        assertEquals(ViewTabLabel.IconAndTitle("mdi:home", "Home"), viewTabLabel("Home", "mdi:home", true))
    }

    @Test
    fun `Given a view without an icon when labelling its tab then its title, or none for unnamed`() {
        assertEquals(ViewTabLabel.TitleOnly("Home"), viewTabLabel("Home", null, true))
        assertEquals(ViewTabLabel.TitleOnly(null), viewTabLabel("", "", false))
    }
}
