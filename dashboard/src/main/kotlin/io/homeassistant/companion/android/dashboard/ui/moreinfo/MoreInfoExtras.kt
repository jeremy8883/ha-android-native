package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.ConfiguratorMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.MoreInfoExtra
import io.homeassistant.companion.android.dashboard.moreinfo.SunRow
import io.homeassistant.companion.android.dashboard.ui.components.ServerImage
import io.homeassistant.companion.android.dashboard.ui.lovelace.cards.MarkdownText
import io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows.RowDateTimeControl
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoCamera
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoGroup
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoPerson
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoScript
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoWeather
import java.time.Instant

/**
 * The details of the domains with controls of their own beyond the shared ones.
 *
 * Upstream: part of `more-info-content` (src/dialogs/more-info/more-info-content.ts;
 * src/dialogs/more-info/state_more_info_control.ts)
 */
@Composable
internal fun MoreInfoExtraView(
    extra: MoreInfoExtra,
    state: EntityState,
    hass: HassSnapshot,
    now: Instant,
    onAction: (CardAction) -> Unit,
) {
    when (extra) {
        is MoreInfoExtra.Sun -> MoreInfoSun(extra.rows)
        is MoreInfoExtra.DateTime -> RowDateTimeControl(extra.control, onAction)
        is MoreInfoExtra.Image -> ServerImage(
            extra.path,
            extra.name,
            Modifier.fillMaxWidth().wrapContentHeight(),
            contentScale = ContentScale.FillWidth,
        )
        is MoreInfoExtra.Group -> MoreInfoGroup(extra, hass, now, onAction)
        is MoreInfoExtra.Person -> MoreInfoPerson(extra, onAction)
        is MoreInfoExtra.Script -> MoreInfoScript(extra.script, state, hass, now, onAction)
        is MoreInfoExtra.Camera -> MoreInfoCamera(extra, state)
        is MoreInfoExtra.Weather -> MoreInfoWeather(extra.weather, state, hass, now)
        // Drawn by the sheet, filling it
        is MoreInfoExtra.Conversation -> Unit
        is MoreInfoExtra.Configurator -> MoreInfoConfigurator(extra.configurator, onAction)
    }
}

/**
 * Port of `more-info-sun`: a rule, then each row's name (with when, for the rising and setting) and value.
 *
 * Upstream: `more-info-sun` (src/dialogs/more-info/controls/more-info-sun.ts)
 */
@Composable
private fun MoreInfoSun(rows: List<SunRow>) {
    val colors = LocalHAColorScheme.current
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        HAHorizontalDivider()
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // `.key`: the name, then when, on one line
                Text(
                    listOfNotNull(row.name, row.relative).joinToString(" "),
                    style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(row.value, style = HATextStyle.Body, color = colors.colorTextPrimary)
            }
        }
    }
}

/**
 * Port of `more-info-configurator`: the description, errors, fields and the submit button.
 *
 * Upstream: `more-info-configurator` (src/dialogs/more-info/controls/more-info-configurator.ts)
 */
@Composable
private fun MoreInfoConfigurator(configurator: ConfiguratorMoreInfo, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    val values = remember(configurator) { mutableStateMapOf<String, String>() }
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        configurator.description?.let { MarkdownText(it, Modifier.fillMaxWidth()) }
        configurator.errors?.let { Text(it, style = HATextStyle.BodyMedium, color = colors.colorOnDangerNormal) }
        configurator.fields.forEach { field ->
            HATextField(
                value = values[field.id].orEmpty(),
                onValueChange = { values[field.id] = it },
                label = { Text(field.name) },
                visualTransformation = if (field.type ==
                    "password"
                ) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (field.type == "number") KeyboardType.Number else KeyboardType.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        configurator.submit?.let { submit ->
            HAAccentButton(submit, onClick = {
                onAction(
                    CardAction.CallService(
                        "configurator",
                        "configure",
                        configurator.configureData(values.toMap()),
                        target = null,
                    ),
                )
            })
        }
    }
}
