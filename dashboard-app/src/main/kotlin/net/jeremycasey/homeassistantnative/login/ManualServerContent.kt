package net.jeremycasey.homeassistantnative.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Close
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import net.jeremycasey.homeassistantnative.R

/**
 * The server's address typed in, as the companion app's manual server screen (app/src/main/kotlin/io/homeassistant/
 * companion/android/onboarding/manualserver/ManualServerScreen.kt), with why connecting to it failed.
 */
@Composable
internal fun ColumnScope.ManualServerContent(
    state: LoginUiState.ChooseServer,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Image(
        painter = painterResource(R.drawable.ic_manual_server),
        contentDescription = null,
        modifier = Modifier.padding(top = HADimens.SPACE6),
    )
    Text(text = stringResource(commonR.string.manual_server_title), style = HATextStyle.Headline)
    HATextField(
        value = state.address,
        onValueChange = onAddressChange,
        modifier = Modifier.widthIn(max = MaxButtonWidth).fillMaxWidth(),
        enabled = !state.connecting,
        isError = state.problem != null,
        trailingIcon = if (state.address.isNotEmpty()) {
            {
                IconButton(onClick = { onAddressChange("") }) {
                    Icon(
                        Mdi.Close.rememberImageVector(),
                        contentDescription = stringResource(commonR.string.clear_text),
                    )
                }
            }
        } else {
            null
        },
        placeholder = {
            Text(
                text = stringResource(R.string.login_address_example),
                style = HATextStyle.UserInput,
                color = LocalHAColorScheme.current.colorOnNeutralNormal,
            )
        },
        supportingText = state.problem?.let { problem -> { Text(problemText(problem)) } },
        // As the companion app: no autocorrection, which rewrites host names as they are typed
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onConnect() }),
        singleLine = true,
    )
    Spacer(modifier = Modifier.weight(1f))
    if (state.connecting) {
        HALoading(Modifier.padding(bottom = HADimens.SPACE6))
    } else {
        HAAccentButton(
            text = stringResource(commonR.string.manual_server_connect),
            onClick = onConnect,
            enabled = state.address.isNotBlank(),
            modifier = Modifier.widthIn(max = MaxButtonWidth).fillMaxWidth().padding(bottom = HADimens.SPACE6),
        )
    }
}
