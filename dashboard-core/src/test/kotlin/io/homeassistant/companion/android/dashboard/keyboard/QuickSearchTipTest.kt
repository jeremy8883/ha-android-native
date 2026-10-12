package io.homeassistant.companion.android.dashboard.keyboard

import io.homeassistant.companion.android.dashboard.entity.JsonTranslations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QuickSearchTipTest {
    @Test
    fun `Given the English translations when building the quick bar's tip then it is the frontend's`() {
        assertEquals(
            QuickSearchTip(
                prefix = "Tip!",
                before = "Press ",
                link = "keyboard shortcut",
                after = " 'Ctrl+k' on any page to open quick search",
            ),
            quickSearchTip(JsonTranslations.bundled()!!),
        )
    }
}
