package io.homeassistant.companion.android.dashboard.derive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ImageFitModeTest {
    @Test
    fun `Given aspect ratios in upstream's forms when parsing them then they give width over height`() {
        assertEquals(16.0 / 9, parseAspectRatio("16x9"))
        assertEquals(16.0 / 9, parseAspectRatio("16:9"))
        assertEquals(1.78, parseAspectRatio("1.78x1"))
        assertEquals(1.78, parseAspectRatio("1.78"))
        assertEquals(100.0 / 56.25, parseAspectRatio("56.25%"))
    }

    @Test
    fun `Given invalid or empty aspect ratios when parsing them then there is none`() {
        assertNull(parseAspectRatio(null))
        assertNull(parseAspectRatio(""))
        assertNull(parseAspectRatio("wide"))
        assertNull(parseAspectRatio("0x9"))
        assertNull(parseAspectRatio("16x"))
    }

    @Test
    fun `Given fit modes when reading them then cover is the default`() {
        assertEquals(ImageFitMode.CONTAIN, imageFitMode("contain"))
        assertEquals(ImageFitMode.FILL, imageFitMode("fill"))
        assertEquals(ImageFitMode.COVER, imageFitMode(null))
    }
}
