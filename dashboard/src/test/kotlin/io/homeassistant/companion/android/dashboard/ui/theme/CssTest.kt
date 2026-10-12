package io.homeassistant.companion.android.dashboard.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CssTest {

    @Test
    fun `Given a CSS px value when converted then it is the same number of dp`() {
        assertEquals(12.dp, 12.cssPx)
        assertEquals(1.5.dp, 1.5.cssPx)
    }

    @Test
    fun `Given a CSS px font size when converted then it is the same number of sp`() {
        assertEquals(36.sp, 36.cssFontPx)
        assertEquals(22.4.sp, 22.4.cssFontPx)
    }

    @Test
    fun `Given the font size variables then they match typography globals`() {
        val expected = listOf(10, 12, 14, 16, 20, 24, 28, 32, 40).map { it.sp }
        val actual = with(Css.FontSize) { listOf(Xs, S, M, L, Xl, X2l, X3l, X4l, X5l) }
        assertEquals(expected, actual)
    }

    @Test
    fun `Given the space variables then each is n times 4px`() {
        val actual = with(Css.Space) {
            listOf(S1, S2, S3, S4, S5, S6, S7, S8, S9, S10, S11, S12, S13, S14, S15, S16, S17, S18, S19, S20)
        }
        assertEquals((1..20).map { (it * 4).dp }, actual)
    }

    @Test
    fun `Given the radius variables then they match core globals`() {
        val expected = listOf(0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 9999).map { it.dp }
        val actual = with(Css.Radius) { listOf(Square, Sm, Md, Lg, Xl, X2l, X3l, X4l, X5l, X6l, Pill) }
        assertEquals(expected, actual)
    }

    @Test
    fun `Given the line height variables then they are multiples of the font size`() {
        assertEquals(1.2.em, Css.LineHeight.Condensed)
        assertEquals(1.6.em, Css.LineHeight.Normal)
        assertEquals(2.em, Css.LineHeight.Expanded)
    }

    @Test
    fun `Given the semantic font weights then they alias the scale`() {
        assertEquals(400, Css.FontWeight.Body.weight)
        assertEquals(700, Css.FontWeight.Heading.weight)
        assertEquals(500, Css.FontWeight.Action.weight)
        assertEquals(300, Css.FontWeight.Light.weight)
    }
}
