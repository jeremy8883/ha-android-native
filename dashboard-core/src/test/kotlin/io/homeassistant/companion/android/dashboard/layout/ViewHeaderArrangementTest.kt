package io.homeassistant.companion.android.dashboard.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ViewHeaderArrangementTest {
    @Test
    fun `Given the default header when arranging then card over badges centred and wrapped`() {
        assertEquals(
            ViewHeaderArrangement(
                HeaderFlow.Stacked(centered = true, badgesFirst = false),
                BadgesArrangement(scroll = false, alignment = BadgesAlignment.Center),
            ),
            viewHeaderArrangement("center", "bottom", "wrap", hasCard = true, widthDp = 400),
        )
    }

    @Test
    fun `Given start layout with badges on top when arranging then badges first at the start`() {
        assertEquals(
            ViewHeaderArrangement(
                HeaderFlow.Stacked(centered = false, badgesFirst = true),
                BadgesArrangement(scroll = false, alignment = BadgesAlignment.Start),
            ),
            viewHeaderArrangement("start", "top", "wrap", hasCard = true, widthDp = 400),
        )
    }

    @Test
    fun `Given scrolling badges when arranging then centred spreads them and start keeps them at the start`() {
        assertEquals(
            BadgesArrangement(scroll = true, alignment = BadgesAlignment.SpaceAround),
            viewHeaderArrangement("center", "bottom", "scroll", hasCard = true, widthDp = 400).badges,
        )
        assertEquals(
            BadgesArrangement(scroll = true, alignment = BadgesAlignment.Start),
            viewHeaderArrangement("start", "bottom", "scroll", hasCard = true, widthDp = 400).badges,
        )
    }

    @Test
    fun `Given responsive layout on a wide screen with a card when arranging then side by side, wrapping at the end`() {
        assertEquals(
            ViewHeaderArrangement(
                HeaderFlow.SideBySide(alignTop = false),
                BadgesArrangement(scroll = false, alignment = BadgesAlignment.End),
            ),
            viewHeaderArrangement("responsive", "bottom", "scroll", hasCard = true, widthDp = 768),
        )
        assertEquals(
            HeaderFlow.SideBySide(alignTop = true),
            viewHeaderArrangement("responsive", "top", "wrap", hasCard = true, widthDp = 1000).flow,
        )
    }

    @Test
    fun `Given responsive layout on a phone or without a card when arranging then stacked at the start`() {
        val stacked = HeaderFlow.Stacked(centered = false, badgesFirst = false)
        assertEquals(stacked, viewHeaderArrangement("responsive", "bottom", "wrap", hasCard = true, widthDp = 767).flow)
        assertEquals(stacked, viewHeaderArrangement("responsive", "bottom", "wrap", hasCard = false, widthDp = 1000).flow)
        assertEquals(stacked, viewHeaderArrangement("responsive", "bottom", "wrap", hasCard = true, widthDp = null).flow)
    }
}
