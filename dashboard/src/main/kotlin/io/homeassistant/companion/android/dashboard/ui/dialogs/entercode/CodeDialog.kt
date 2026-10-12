package io.homeassistant.companion.android.dashboard.ui.dialogs.entercode

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.dashboard.action.CodeRequest
import io.homeassistant.companion.android.dashboard.ui.theme.Css

/**
 * Asks for the code of a protected lock or alarm, like upstream's enter-code dialog.
 *
 * Upstream: `dialog-enter-code` (src/dialogs/enter-code/dialog-enter-code.ts)
 */
@Composable
internal fun CodeDialog(request: CodeRequest, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.title, style = Css.Text.DialogTitle) },
        text = {
            // UPSTREAM-DIFF: the system keyboard (numbers for a number code) instead of upstream's own keypad
            // with clear and submit keys.
            HATextField(
                value = code,
                onValueChange = { code = it },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (request.codeFormat ==
                        NUMBER_FORMAT
                    ) {
                        KeyboardType.NumberPassword
                    } else {
                        KeyboardType.Password
                    },
                ),
                singleLine = true,
            )
        },
        confirmButton = { HAPlainButton(request.title, { onSubmit(code) }, enabled = code.isNotEmpty()) },
        dismissButton = { HAPlainButton(stringResource(commonR.string.cancel), onDismiss) },
    )
}

private const val NUMBER_FORMAT = "number"
