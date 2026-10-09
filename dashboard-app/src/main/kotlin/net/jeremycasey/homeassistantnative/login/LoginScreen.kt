package net.jeremycasey.homeassistantnative.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.data.LoadError
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import net.jeremycasey.homeassistantnative.R

/**
 * Logging in to a server: choosing it, then the login flow's forms, natively.
 *
 * @param onLoggedIn called with the added server once logged in
 * @param onClose leaves without logging in, when there is somewhere to go back to (another server)
 */
@Composable
internal fun LoginScreen(onLoggedIn: (Int) -> Unit, onClose: (() -> Unit)? = null) {
    val viewModel: LoginViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val discovery by viewModel.discovery.collectAsStateWithLifecycle()
    val currentOnLoggedIn by rememberUpdatedState(onLoggedIn)
    LaunchedEffect(viewModel) { viewModel.loggedIn.collect { currentOnLoggedIn(it) } }
    BackHandler(enabled = state !is LoginUiState.ChooseServer) { viewModel.onBack() }
    onClose?.let { BackHandler(enabled = state is LoginUiState.ChooseServer, onBack = it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(HADimens.SPACE6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = MAX_WIDTH.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            when (val current = state) {
                is LoginUiState.ChooseServer -> ChooseServer(current, discovery, viewModel)
                is LoginUiState.ChooseProvider -> ChooseProvider(current, viewModel::onPickProvider)
                is LoginUiState.Form -> LoginForm(current, viewModel)
                is LoginUiState.Aborted -> {
                    Text(current.message, style = HATextStyle.Body)
                    HAAccentButton(current.startOver, viewModel::onStartOver, Modifier.fillMaxWidth())
                }
                is LoginUiState.SigningIn -> Waiting(stringResource(R.string.login_signing_in))
            }
        }
    }
}

@Composable
private fun ColumnScope.ChooseServer(
    state: LoginUiState.ChooseServer,
    discovery: Discovery,
    viewModel: LoginViewModel,
) {
    Text(stringResource(R.string.login_title), style = HATextStyle.Headline)
    if (discovery.servers.isNotEmpty()) {
        Text(stringResource(R.string.login_found), style = HATextStyle.BodyMedium)
        discovery.servers.forEach { server ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.connecting, role = Role.Button) { viewModel.onConnect(server) }
                    .padding(vertical = HADimens.SPACE2),
            ) {
                Text(server.name, style = HATextStyle.Body)
                Text(server.url.toString(), style = HATextStyle.BodyMedium)
            }
        }
    } else {
        val searching = if (discovery.failed) R.string.login_search_failed else R.string.login_searching
        Text(stringResource(searching), style = HATextStyle.BodyMedium)
    }
    HATextField(
        value = state.address,
        onValueChange = viewModel::onAddressChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.connecting,
        isError = state.problem != null,
        label = { Text(stringResource(R.string.login_address)) },
        placeholder = { Text(stringResource(R.string.login_address_example)) },
        supportingText = state.problem?.let { problem -> { Text(problemText(problem)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.onConnect() }),
        singleLine = true,
    )
    if (state.connecting) {
        HALoading(Modifier.align(Alignment.CenterHorizontally))
    } else {
        HAAccentButton(stringResource(R.string.login_connect), { viewModel.onConnect() }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChooseProvider(state: LoginUiState.ChooseProvider, onPick: (AuthProvider) -> Unit) {
    Text(state.title, style = HATextStyle.Headline)
    state.providers.forEach { provider ->
        HAPlainButton(provider.name, { onPick(provider) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ColumnScope.LoginForm(state: LoginUiState.Form, viewModel: LoginViewModel) {
    val texts = state.texts
    Text(texts.title, style = HATextStyle.Headline)
    texts.description?.let { Text(it, style = HATextStyle.Body) }
    state.step.fields.forEach { field ->
        LoginFieldInput(
            field = field,
            label = texts.labels[field.name] ?: field.name,
            value = state.values[field.name],
            error = texts.errors[field.name],
            enabled = !state.submitting,
            onValueChange = { viewModel.onValueChange(field.name, it) },
            onDone = viewModel::onSubmit,
        )
    }
    val problem = texts.errors[BASE_ERROR] ?: state.problem?.let { problemText(it) }
    problem?.let { Text(it, style = HATextStyle.Body, color = LocalHAColorScheme.current.colorOnDangerNormal) }
    if (state.submitting) {
        HALoading(Modifier.align(Alignment.CenterHorizontally))
    } else {
        HAAccentButton(texts.submit, viewModel::onSubmit, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LoginFieldInput(
    field: LoginField,
    label: String,
    value: JsonPrimitive?,
    error: String?,
    enabled: Boolean,
    onValueChange: (JsonPrimitive) -> Unit,
    onDone: () -> Unit,
) {
    when (val type = field.type) {
        LoginFieldType.Text -> {
            val secret = field.name == PASSWORD
            HATextField(
                value = value?.content.orEmpty(),
                onValueChange = { onValueChange(JsonPrimitive(it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                isError = error != null,
                label = { Text(label) },
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (field.name) {
                        PASSWORD -> KeyboardType.Password
                        CODE -> KeyboardType.NumberPassword
                        else -> KeyboardType.Text
                    },
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            )
        }
        is LoginFieldType.Select -> {
            Text(label, style = HATextStyle.BodyMedium)
            HARadioGroup(
                options = type.options.map { (option, optionLabel) -> RadioOption(option, optionLabel) },
                onSelect = { onValueChange(JsonPrimitive(it.selectionKey)) },
                selectionKey = value?.content,
            )
        }
        LoginFieldType.Toggle -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = HATextStyle.Body, modifier = Modifier.weight(1f))
            HASwitch(checked = value?.booleanOrNull == true, onCheckedChange = { onValueChange(JsonPrimitive(it)) })
        }
    }
}

@Composable
private fun Waiting(text: String) {
    HALoading()
    Text(text, style = HATextStyle.Body)
}

@Composable
private fun problemText(problem: LoadError): String = when (problem) {
    LoadError.NoResponse, LoadError.NoServer -> stringResource(R.string.login_error_unreachable)
    is LoadError.Server -> if (problem.code == ONBOARDING_REQUIRED) {
        stringResource(R.string.login_error_onboarding)
    } else {
        stringResource(R.string.login_error_server, problem.message ?: problem.code.orEmpty())
    }
    is LoadError.UnexpectedResponse -> if (problem.command == ADDRESS) {
        stringResource(R.string.login_error_address)
    } else {
        stringResource(R.string.login_error_unexpected)
    }
}

private const val MAX_WIDTH = 480
private const val PASSWORD = "password"
private const val CODE = "code"
private const val BASE_ERROR = "base"
private const val ONBOARDING_REQUIRED = "onboarding_required"
private const val ADDRESS = "address"
