package net.jeremycasey.homeassistantnative.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.dashboard.data.LoadError
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import net.jeremycasey.homeassistantnative.R

/**
 * Logging in to a server, styled as the companion app's onboarding: choosing the server (found on the network, or its
 * address), then the server's login forms, natively.
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
    val current = state
    val atStart = current is LoginUiState.ChooseServer && !current.manual
    val onBack: (() -> Unit)? = if (atStart) onClose else ({ viewModel.onBack() })
    BackHandler(enabled = onBack != null) { onBack?.invoke() }

    Scaffold(
        topBar = { HATopBar(onBackClick = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = LocalHAColorScheme.current.colorSurfaceDefault,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HADimens.SPACE4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (current is LoginUiState.ChooseServer && !current.manual) {
                Arrangement.Top
            } else {
                Arrangement.spacedBy(HADimens.SPACE6)
            },
        ) {
            when (current) {
                is LoginUiState.ChooseServer -> if (current.manual) {
                    ManualServerContent(current, viewModel::onAddressChange) { viewModel.onConnect() }
                } else {
                    ServerDiscoveryContent(
                        discovery,
                        current.connecting,
                        viewModel::onConnect,
                        viewModel::onManualSetup,
                    )
                }
                is LoginUiState.ChooseProvider -> ChooseProvider(current, viewModel::onPickProvider)
                is LoginUiState.Form -> LoginForm(current, viewModel)
                is LoginUiState.Aborted -> Aborted(current, viewModel::onStartOver)
                is LoginUiState.SigningIn -> SigningIn()
            }
        }
    }
}

/** The Home Assistant logo and a title, as the top of the frontend's login page. */
@Composable
private fun ColumnScope.LoginHeader(title: String, description: String? = null) {
    Spacer(modifier = Modifier.weight(HEADER_POSITION))
    Image(
        imageVector = ImageVector.vectorResource(R.drawable.ic_home_assistant_branding),
        contentDescription = stringResource(commonR.string.home_assistant_branding_icon_content_description),
        modifier = Modifier.size(ICON_SIZE),
    )
    Text(title, style = HATextStyle.Headline, modifier = Modifier.widthIn(max = MaxButtonWidth))
    description?.let { Text(it, style = HATextStyle.Body, modifier = Modifier.widthIn(max = MaxButtonWidth)) }
}

@Composable
private fun ColumnScope.ChooseProvider(state: LoginUiState.ChooseProvider, onPick: (AuthProvider) -> Unit) {
    LoginHeader(state.title)
    state.providers.forEach { provider ->
        HAPlainButton(provider.name, { onPick(provider) }, Modifier.widthIn(max = MaxButtonWidth).fillMaxWidth())
    }
    Spacer(modifier = Modifier.weight(1f - HEADER_POSITION))
}

@Composable
private fun ColumnScope.LoginForm(state: LoginUiState.Form, viewModel: LoginViewModel) {
    val texts = state.texts
    LoginHeader(texts.title, texts.description)
    Column(
        modifier = Modifier.widthIn(max = MaxButtonWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
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
    }
    Spacer(modifier = Modifier.weight(1f - HEADER_POSITION))
    if (state.submitting) {
        HALoading(Modifier.padding(bottom = HADimens.SPACE6))
    } else {
        HAAccentButton(
            texts.submit,
            viewModel::onSubmit,
            Modifier.widthIn(max = MaxButtonWidth).fillMaxWidth().padding(bottom = HADimens.SPACE6),
        )
    }
}

@Composable
private fun ColumnScope.Aborted(state: LoginUiState.Aborted, onStartOver: () -> Unit) {
    LoginHeader(state.message)
    Spacer(modifier = Modifier.weight(1f - HEADER_POSITION))
    HAAccentButton(
        state.startOver,
        onStartOver,
        Modifier.widthIn(max = MaxButtonWidth).fillMaxWidth().padding(bottom = HADimens.SPACE6),
    )
}

/** Logged in, while the server is added: a loader in the middle of the screen. */
@Composable
private fun ColumnScope.SigningIn() {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            HALoading()
            Text(stringResource(R.string.login_signing_in), style = HATextStyle.Body)
        }
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
internal fun problemText(problem: LoadError): String = when (problem) {
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

private const val PASSWORD = "password"
private const val CODE = "code"
private const val BASE_ERROR = "base"
private const val ONBOARDING_REQUIRED = "onboarding_required"
private const val ADDRESS = "address"
private const val HEADER_POSITION = 0.2f
