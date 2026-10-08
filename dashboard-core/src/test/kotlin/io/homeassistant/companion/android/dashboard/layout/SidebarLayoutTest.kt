package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.condition.ScreenInfo
import io.homeassistant.companion.android.dashboard.condition.sectionsViewColumns
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The generated home overview's summaries sidebar at phone, in-between and tablet widths. */
class SidebarLayoutTest {
    private val fixture = GoldenFixture("test-instance")
    private val overview = ViewConfig(
        fixture.json("outputs/expanded.json").objects("views").first { it.string("path") == "overview" },
    )

    private fun layoutAt(widthDp: Int): ViewLayout {
        val columns = sectionsViewColumns(widthDp, overview.json.number("max_columns")?.toInt())
        val context = ConditionContext(maxColumns = columns, screen = ScreenInfo(widthDp, HEIGHT))
        return fixture.hass.viewLayout(cardGroups(overview), context, viewSidebar(overview))
    }

    @Test
    fun `Given a phone when laying out the overview then the sidebar is hidden and summaries are inline`() {
        val layout = layoutAt(PHONE)
        assertNull(layout.sidebar)
        assertEquals(1, layout.columnCount)
    }

    @Test
    fun `Given a tablet when laying out the overview then the summaries sidebar takes a column`() {
        val layout = layoutAt(TABLET)
        assertEquals(SidebarLayout.MODE_COLUMN, layout.sidebar?.mode)
        assertEquals(2, layout.columnCount)
        assertEquals("Summaries", layout.sidebar?.sidebarLabel)
    }

    @Test
    fun `Given a narrow two column screen when laying out the overview then the sidebar is a tab`() {
        val layout = layoutAt(NARROW_TWO_COLUMNS)
        assertEquals(SidebarLayout.MODE_TABS, layout.sidebar?.mode)
        assertEquals(2, layout.columnCount)
    }

    private companion object {
        const val PHONE = 411
        const val NARROW_TWO_COLUMNS = 800
        const val TABLET = 1280
        const val HEIGHT = 900
    }
}
