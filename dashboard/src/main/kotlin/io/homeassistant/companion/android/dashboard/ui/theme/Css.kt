package io.homeassistant.companion.android.dashboard.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/*
 * The frontend's CSS sizes in Compose, so porting a component never needs a size conversion.
 *
 * 1 CSS px is 1 dp: the browser already maps CSS px to device pixels by the screen's density, as Android does dp.
 * A font size or line height in px is the same number in sp, which equals dp at the default font scale, as CSS px do
 * at the browser's default zoom; a larger Android font scale grows text as browser zoom would.
 *
 * - A raw px value in the CSS: `padding: 12px` is `12.cssPx`, `font-size: 36px` is `36.cssFontPx`.
 * - A CSS variable: the token named after it, `var(--ha-space-4)` is `Css.Space.S4`, `var(--ha-font-size-m)` is
 *   `Css.FontSize.M`, `var(--ha-border-radius-md)` is `Css.Radius.Md`, `var(--ha-line-height-normal)` is
 *   `Css.LineHeight.Normal`.
 * - Text: [Css.Text] holds the composed styles the frontend uses most.
 *
 * docs/ui-handover.md has the full table.
 */

/** A CSS length in px as dp: `margin: 12px` is `12.cssPx`. */
internal val Int.cssPx: Dp get() = dp

/** A fractional CSS length in px as dp: `width: 1.5px` is `1.5.cssPx`. */
internal val Double.cssPx: Dp get() = dp

/** A CSS `font-size` or `line-height` in px as sp: `font-size: 36px` is `36.cssFontPx`. */
internal val Int.cssFontPx: TextUnit get() = sp

/** A fractional CSS `font-size` or `line-height` in px as sp: `line-height: 22.4px` is `22.4.cssFontPx`. */
internal val Double.cssFontPx: TextUnit get() = sp

/**
 * `--ha-font-size-scale`, the frontend's multiplier for every `--ha-font-size-*` (1 unless a theme sets it). Raw px
 * font sizes ignore it upstream, so only [Css.FontSize] applies it: this is the one place a scale would go.
 */
private const val FONT_SIZE_SCALE = 1f

private fun scaled(size: TextUnit): TextUnit = size * FONT_SIZE_SCALE

/**
 * The frontend's CSS variables (`src/resources/theme/typography.globals.ts`, `core.globals.ts`) as Compose values.
 *
 * The values HATheme (`:common`) already shares with the frontend are aliased by their full name: this file is the
 * one place in :dashboard that reads them (dashboard/detekt.yml forbids importing them elsewhere).
 */
internal object Css {

    /** `--ha-font-size-*`, through `--ha-font-size-scale`. */
    object FontSize {
        /** `--ha-font-size-xs`: 10px. */
        val Xs = scaled(io.homeassistant.companion.android.common.compose.theme.HAFontSize.XS)

        /** `--ha-font-size-s`: 12px. */
        val S = scaled(io.homeassistant.companion.android.common.compose.theme.HAFontSize.S)

        /** `--ha-font-size-m`: 14px, the body text. */
        val M = scaled(io.homeassistant.companion.android.common.compose.theme.HAFontSize.M)

        /** `--ha-font-size-l`: 16px. */
        val L = scaled(io.homeassistant.companion.android.common.compose.theme.HAFontSize.L)

        /** `--ha-font-size-xl`: 20px. */
        val Xl = scaled(io.homeassistant.companion.android.common.compose.theme.HAFontSize.XL)

        /** `--ha-font-size-2xl`: 24px. */
        val X2l = scaled(io.homeassistant.companion.android.common.compose.theme.HAFontSize.X2L)

        /** `--ha-font-size-3xl`: 28px. */
        val X3l = scaled(io.homeassistant.companion.android.common.compose.theme.HAFontSize.X3L)

        /** `--ha-font-size-4xl`: 32px. */
        val X4l = scaled(io.homeassistant.companion.android.common.compose.theme.HAFontSize.X4L)

        /** `--ha-font-size-5xl`: 40px. */
        val X5l = scaled(io.homeassistant.companion.android.common.compose.theme.HAFontSize.X5L)
    }

    /** `--ha-font-weight-*`. */
    object FontWeight {
        /** `--ha-font-weight-light`: 300. */
        val Light = ComposeFontWeight.Light

        /** `--ha-font-weight-normal`: 400. */
        val Normal = ComposeFontWeight.Normal

        /** `--ha-font-weight-medium`: 500. */
        val Medium = ComposeFontWeight.Medium

        /** `--ha-font-weight-bold`: 700. */
        val Bold = ComposeFontWeight.Bold

        /** `--ha-font-weight-body`: [Normal]. */
        val Body = Normal

        /** `--ha-font-weight-heading`: [Bold]. */
        val Heading = Bold

        /** `--ha-font-weight-action`: [Medium]. */
        val Action = Medium
    }

    /** `--ha-line-height-*`: unitless in CSS, so a multiple of the text's own font size (em). */
    object LineHeight {
        /** `--ha-line-height-condensed`: 1.2. */
        val Condensed = 1.2.em

        /** `--ha-line-height-normal`: 1.6. */
        val Normal = 1.6.em

        /** `--ha-line-height-expanded`: 2. */
        val Expanded = 2.em
    }

    /** `--ha-space-*`: `--ha-space-n` is n × 4px. */
    object Space {
        /** `--ha-space-1`: 4px. */
        val S1 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE1

        /** `--ha-space-2`: 8px. */
        val S2 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE2

        /** `--ha-space-3`: 12px. */
        val S3 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE3

        /** `--ha-space-4`: 16px. */
        val S4 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE4

        /** `--ha-space-5`: 20px. */
        val S5 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE5

        /** `--ha-space-6`: 24px. */
        val S6 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE6

        /** `--ha-space-7`: 28px. */
        val S7 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE7

        /** `--ha-space-8`: 32px. */
        val S8 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE8

        /** `--ha-space-9`: 36px. */
        val S9 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE9

        /** `--ha-space-10`: 40px. */
        val S10 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE10

        /** `--ha-space-11`: 44px. */
        val S11 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE11

        /** `--ha-space-12`: 48px. */
        val S12 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE12

        /** `--ha-space-13`: 52px. */
        val S13 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE13

        /** `--ha-space-14`: 56px. */
        val S14 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE14

        /** `--ha-space-15`: 60px. */
        val S15 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE15

        /** `--ha-space-16`: 64px. */
        val S16 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE16

        /** `--ha-space-17`: 68px. */
        val S17 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE17

        /** `--ha-space-18`: 72px. */
        val S18 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE18

        /** `--ha-space-19`: 76px. */
        val S19 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE19

        /** `--ha-space-20`: 80px. */
        val S20 = io.homeassistant.companion.android.common.compose.theme.HADimens.SPACE20
    }

    /** `--ha-border-radius-*`. `--ha-border-radius-circle` (50%) is Compose's `CircleShape`. */
    object Radius {
        /** `--ha-border-radius-square`: 0. */
        val Square = 0.cssPx

        /** `--ha-border-radius-sm`: 4px. */
        val Sm = 4.cssPx

        /** `--ha-border-radius-md`: 8px. */
        val Md = 8.cssPx

        /** `--ha-border-radius-lg`: 12px. */
        val Lg = 12.cssPx

        /** `--ha-border-radius-xl`: 16px. */
        val Xl = 16.cssPx

        /** `--ha-border-radius-2xl`: 20px. */
        val X2l = 20.cssPx

        /** `--ha-border-radius-3xl`: 24px. */
        val X3l = 24.cssPx

        /** `--ha-border-radius-4xl`: 28px. */
        val X4l = 28.cssPx

        /** `--ha-border-radius-5xl`: 32px. */
        val X5l = 32.cssPx

        /** `--ha-border-radius-6xl`: 36px. */
        val X6l = 36.cssPx

        /** `--ha-border-radius-pill`: 9999px, fully rounded ends. */
        val Pill = 9999.cssPx
    }

    /** `--ha-border-width-*`. */
    object BorderWidth {
        /** `--ha-border-width-sm`: 1px. */
        val Sm = io.homeassistant.companion.android.common.compose.theme.HABorderWidth.S

        /** `--ha-border-width-md`: 2px. */
        val Md = io.homeassistant.companion.android.common.compose.theme.HABorderWidth.M

        /** `--ha-border-width-lg`: 3px. */
        val Lg = io.homeassistant.companion.android.common.compose.theme.HABorderWidth.L
    }

    /** The text styles the frontend uses most, drawn in HATheme's colours. */
    object Text {

        /**
         * The frontend's body text (`haStyle`'s `:host`): `--ha-font-size-m` at `--ha-line-height-normal`, normal
         * weight. Centred and in the secondary text colour, so set `color`/`textAlign` where the frontend shows
         * primary or start-aligned text. Its line height is fixed (22.4px), so a copy at another size keeps it.
         */
        val Body
            @Composable
            get() = TextStyle(
                fontStyle = FontStyle.Normal,
                fontSize = FontSize.M,
                lineHeight = BODY_LINE_HEIGHT,
                fontWeight = FontWeight.Body,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp,
                color = LocalHAColorScheme.current.colorTextSecondary,
            )

        /**
         * [Body] start-aligned in the primary text colour, the frontend's default for text (`--primary-text-color`)
         * and the base of its titles.
         */
        val Primary
            @Composable
            get() = Body.copy(textAlign = TextAlign.Start, color = LocalHAColorScheme.current.colorTextPrimary)

        /** `ha-card`'s `.card-header`: `--ha-font-size-2xl`, normal weight, at `--ha-line-height-expanded`. */
        val CardHeader
            @Composable
            get() = TextStyle(
                fontSize = FontSize.X2l,
                lineHeight = LineHeight.Expanded,
                letterSpacing = CARD_HEADER_LETTER_SPACING.em,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Start,
                color = LocalHAColorScheme.current.colorTextPrimary,
            )

        /**
         * A dialog's title, `ha-dialog-header`'s `.header-title`: `--ha-font-size-xl`, medium weight, at
         * `--ha-line-height-condensed`, start-aligned.
         */
        val DialogTitle
            @Composable
            get() = Primary.copy(
                fontSize = FontSize.Xl,
                fontWeight = FontWeight.Medium,
                lineHeight = LineHeight.Condensed,
            )

        /** `haStyle`'s `h1`: `--ha-font-size-2xl`, normal weight, at `--ha-line-height-condensed`. */
        val H1
            @Composable
            get() = Primary.copy(
                fontSize = FontSize.X2l,
                fontWeight = FontWeight.Normal,
                lineHeight = LineHeight.Condensed,
            )

        /** `haStyle`'s `h2`: `--ha-font-size-xl`, medium weight, at `--ha-line-height-normal`. */
        val H2
            @Composable
            get() = Primary.copy(fontSize = FontSize.Xl, fontWeight = FontWeight.Medium, lineHeight = LineHeight.Normal)

        /** `haStyle`'s `h3`: `--ha-font-size-l`, normal weight, at `--ha-line-height-normal`. */
        val H3
            @Composable
            get() = Primary.copy(fontSize = FontSize.L, fontWeight = FontWeight.Normal, lineHeight = LineHeight.Normal)
    }
}

/** `ha-card`'s `.card-header` padding: `--ha-space-3` above, `--ha-space-4` on the sides and below. */
internal val CardHeaderPadding = PaddingValues(
    start = Css.Space.S4,
    top = Css.Space.S3,
    end = Css.Space.S4,
    bottom = Css.Space.S4,
)

/**
 * [CardHeaderPadding] over a `.card-content`, whose `margin-top: -8px` (`ha-card`) leaves 8dp of the header's bottom
 * padding.
 */
internal val CardHeaderAboveContentPadding = PaddingValues(
    start = Css.Space.S4,
    top = Css.Space.S3,
    end = Css.Space.S4,
    bottom = Css.Space.S2,
)

/** `--ha-line-height-normal` (1.6) of `--ha-font-size-m`, fixed so a copy at another size keeps the same rhythm. */
internal val BODY_LINE_HEIGHT = 22.4.cssFontPx

/** `.card-header`'s `letter-spacing: -0.012em`. */
private const val CARD_HEADER_LETTER_SPACING = -0.012f
