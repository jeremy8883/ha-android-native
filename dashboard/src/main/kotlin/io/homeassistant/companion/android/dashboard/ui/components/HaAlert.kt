package io.homeassistant.companion.android.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/**
 * Port of `ha-alert`: the type's icon and [text] (under a bold [title] when given) on the type's colour at 12%.
 *
 * Upstream: `ha-alert` (src/components/ha-alert.ts)
 */
@Composable
internal fun HaAlert(
    text: String,
    modifier: Modifier = Modifier,
    type: HaAlertType = HaAlertType.INFO,
    title: String? = null,
) {
    val color = resolveVariable(type.colorVariable, isSystemInDarkTheme()) ?: Color.Gray
    val textColor = LocalHAColorScheme.current.colorTextPrimary
    Row(
        modifier
            .fillMaxWidth()
            .background(color.copy(alpha = BACKGROUND_ALPHA), RoundedCornerShape(HARadius.S))
            .padding(HADimens.SPACE2),
        verticalAlignment = if (title == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        DashboardIcon(type.icon, color, Modifier.size(HASize.X2L))
        Column(Modifier.padding(horizontal = HADimens.SPACE2)) {
            title?.let {
                Text(
                    it,
                    style = HATextStyle.Body,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(top = TITLE_TOP),
                )
            }
            Text(text, style = HATextStyle.Body, color = textColor)
        }
    }
}

/** The kinds of `ha-alert`, each with its icon and the frontend colour variable tinting it. */
internal enum class HaAlertType(val icon: String, val colorVariable: String) {
    INFO("mdi:information-outline", "info-color"),
    WARNING("mdi:alert-outline", "warning-color"),
    ERROR("mdi:alert-circle-outline", "error-color"),
    SUCCESS("mdi:checkbox-marked-circle-outline", "success-color"),
}

private const val BACKGROUND_ALPHA = 0.12f
private val TITLE_TOP = 2.dp
