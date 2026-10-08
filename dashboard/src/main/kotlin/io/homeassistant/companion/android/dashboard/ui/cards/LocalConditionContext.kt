package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.runtime.compositionLocalOf
import io.homeassistant.companion.android.dashboard.condition.ConditionContext

/** The screen and view the cards are shown in, for the visibility conditions inside cards (badges). */
internal val LocalConditionContext = compositionLocalOf { ConditionContext() }
