package io.homeassistant.companion.android.dashboard.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * Text styles at the frontend's sizes (`src/resources/theme/typography.globals.ts`), so a dashboard holds as much on
 * a phone as the web page does, drawn in HATheme's colours. 1 CSS px is 1 dp (sp at the default font scale).
 */
internal object FrontendTextStyle {

    /**
     * The frontend's body text: `--ha-font-size-m` at `--ha-line-height-normal`. Otherwise [HATextStyle.Body]
     * (centred, secondary colour), so it replaces it one for one.
     */
    val Body
        @Composable
        get() = HATextStyle.Body.copy(fontSize = HAFontSize.M, lineHeight = BODY_LINE_HEIGHT)

    /** `ha-card`'s `.card-header`: `--ha-font-size-2xl`, normal weight, at `--ha-line-height-expanded`. */
    val CardHeader
        @Composable
        get() = TextStyle(
            fontSize = HAFontSize.X2L,
            lineHeight = LINE_HEIGHT_EXPANDED.em,
            letterSpacing = CARD_HEADER_LETTER_SPACING.em,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Start,
            color = LocalHAColorScheme.current.colorTextPrimary,
        )
}

/** `ha-card`'s `.card-header` padding: `--ha-space-3` above, `--ha-space-4` on the sides and below. */
internal val CardHeaderPadding = PaddingValues(
    start = HADimens.SPACE4,
    top = HADimens.SPACE3,
    end = HADimens.SPACE4,
    bottom = HADimens.SPACE4,
)

/**
 * [CardHeaderPadding] over a `.card-content`, whose `margin-top: -8px` (`ha-card`) leaves 8dp of the header's bottom
 * padding.
 */
internal val CardHeaderAboveContentPadding = PaddingValues(
    start = HADimens.SPACE4,
    top = HADimens.SPACE3,
    end = HADimens.SPACE4,
    bottom = HADimens.SPACE2,
)

/** `--ha-line-height-normal` (1.6) of `--ha-font-size-m`, fixed so a copy at another size keeps the same rhythm. */
internal val BODY_LINE_HEIGHT = 22.4.sp

/** `--ha-line-height-expanded`. */
private const val LINE_HEIGHT_EXPANDED = 2f

/** `.card-header`'s `letter-spacing: -0.012em`. */
private const val CARD_HEADER_LETTER_SPACING = -0.012f
