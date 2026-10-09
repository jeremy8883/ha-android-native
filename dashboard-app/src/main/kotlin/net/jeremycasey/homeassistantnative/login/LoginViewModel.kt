package net.jeremycasey.homeassistantnative.login

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.entity.JsonTranslations
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import timber.log.Timber

/** Where logging in is at. */
internal sealed interface LoginUiState {
    /**
     * Choosing the server: one found on the network, or, when [manual], an address typed in. A server that couldn't be
     * connected to comes back here with the [problem], its address filled in.
     */
    data class ChooseServer(
        val address: String = "",
        val problem: LoadError? = null,
        val connecting: Boolean = false,
        val manual: Boolean = false,
    ) : LoginUiState

    data class ChooseProvider(val server: HttpUrl, val providers: List<AuthProvider>, val title: String) : LoginUiState

    /**
     * A form of the login flow, with its texts.
     *
     * @property values the answers so far, by field name
     * @property problem why the last request failed, apart from the form's own errors
     */
    data class Form(
        val server: HttpUrl,
        val provider: AuthProvider,
        val step: LoginStep.Form,
        val texts: FormTexts,
        val values: Map<String, JsonPrimitive>,
        val submitting: Boolean = false,
        val problem: LoadError? = null,
    ) : LoginUiState

    data class Aborted(val server: HttpUrl, val provider: AuthProvider, val message: String, val startOver: String) :
        LoginUiState

    /** Logged in; the server is being added. */
    data class SigningIn(val server: HttpUrl) : LoginUiState
}

/** A form's texts, ready to show. */
internal data class FormTexts(
    val title: String,
    val description: String?,
    val labels: Map<String, String>,
    val errors: Map<String, String>,
    val submit: String,
)

/** The servers found on the local network so far, and whether searching failed. */
internal data class Discovery(val servers: List<HomeAssistantInstance>, val failed: Boolean)

/** Logs in to a Home Assistant server natively, through its login API, and adds it as the active server. */
@HiltViewModel
internal class LoginViewModel @VisibleForTesting constructor(
    searcher: HomeAssistantSearcher,
    private val api: LoginFlowApi,
    private val addServer: AddServer,
    ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(searcher: HomeAssistantSearcher, api: LoginFlowApi, addServer: AddServer) :
        this(searcher, api, addServer, Dispatchers.IO)

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.ChooseServer())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _loggedIn = Channel<Int>(Channel.BUFFERED)

    /** The id of the server added once logged in. */
    val loggedIn: Flow<Int> = _loggedIn.receiveAsFlow()

    val discovery: StateFlow<Discovery> = searcher.discoveredInstanceFlow()
        .runningFold(emptyList<HomeAssistantInstance>()) { found, instance ->
            found.filterNot { it.url == instance.url } + instance
        }
        .map { Discovery(it, failed = false) }
        .catch { e ->
            Timber.w(e, "Failed to search the network for servers")
            emit(Discovery(emptyList(), failed = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(DISCOVERY_STOP_TIMEOUT), Discovery(emptyList(), false))

    private val texts = flow { emit(LoginTexts(checkNotNull(JsonTranslations.bundled()) { "Missing translations" })) }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onAddressChange(address: String) {
        _state.update { (it as? LoginUiState.ChooseServer)?.copy(address = address, problem = null) ?: it }
    }

    /** Connect to a server found on the network, or to the address typed in. */
    fun onConnect(instance: HomeAssistantInstance? = null) {
        if (instance != null) {
            serverAddress(instance.url.toString())?.let(::connect)
            return
        }
        val choose = _state.value as? LoginUiState.ChooseServer ?: return
        val server = serverAddress(choose.address)
        if (server == null) {
            _state.value = choose.copy(problem = LoadError.UnexpectedResponse(ADDRESS))
        } else {
            connect(server)
        }
    }

    fun onPickProvider(provider: AuthProvider) {
        val choose = _state.value as? LoginUiState.ChooseProvider ?: return
        start(choose.server, provider)
    }

    fun onValueChange(field: String, value: JsonPrimitive) {
        _state.update { (it as? LoginUiState.Form)?.copy(values = it.values + (field to value), problem = null) ?: it }
    }

    fun onSubmit() {
        val form = _state.value as? LoginUiState.Form ?: return
        if (form.submitting) return
        _state.value = form.copy(submitting = true, problem = null)
        viewModelScope.launch {
            when (val next = api.submit(form.server, form.step.flowId, form.values)) {
                is Fetched.Success -> show(form.server, form.provider, next.value, previous = form)
                is Fetched.Failure -> _state.value = form.copy(submitting = false, problem = next.error)
            }
        }
    }

    /** Start logging in again with the same provider, after an abort or a failure. */
    fun onStartOver() {
        when (val state = _state.value) {
            is LoginUiState.Aborted -> start(state.server, state.provider)
            is LoginUiState.Form -> start(state.server, state.provider)
            else -> Unit
        }
    }

    /** Type the server's address instead of choosing one found on the network. */
    fun onManualSetup() {
        _state.update { (it as? LoginUiState.ChooseServer)?.copy(manual = true) ?: it }
    }

    /** Back a step: to the servers found from the address, or to choosing the server. @return whether there was one */
    fun onBack(): Boolean {
        val state = _state.value
        val back = when {
            state is LoginUiState.ChooseServer && state.manual -> state.copy(manual = false, problem = null)
            state is LoginUiState.ChooseServer -> null
            else -> LoginUiState.ChooseServer()
        }
        back?.let { _state.value = it }
        return back != null
    }

    private fun connect(server: HttpUrl) {
        val manual = (_state.value as? LoginUiState.ChooseServer)?.manual == true
        _state.value = LoginUiState.ChooseServer(address = server.baseUrl(), connecting = true, manual = manual)
        viewModelScope.launch {
            when (val providers = api.providers(server)) {
                is Fetched.Failure -> _state.value = chooseServerAgain(server, providers.error)
                is Fetched.Success -> when (providers.value.size) {
                    0 -> _state.value = chooseServerAgain(server, LoadError.UnexpectedResponse(PROVIDERS))
                    // As the frontend, the first provider unless the user picks another
                    1 -> start(server, providers.value.single())
                    else -> _state.value = LoginUiState.ChooseProvider(
                        server = server,
                        providers = providers.value,
                        title = texts.value?.pickProvider().orEmpty(),
                    )
                }
            }
        }
    }

    private fun start(server: HttpUrl, provider: AuthProvider) {
        viewModelScope.launch {
            when (val step = api.start(server, provider)) {
                is Fetched.Failure -> _state.value = chooseServerAgain(server, step.error)
                is Fetched.Success -> show(server, provider, step.value, previous = null)
            }
        }
    }

    private suspend fun show(server: HttpUrl, provider: AuthProvider, step: LoginStep, previous: LoginUiState.Form?) {
        val texts = texts.value ?: return
        _state.value = when (step) {
            is LoginStep.Form -> LoginUiState.Form(
                server = server,
                provider = provider,
                step = step,
                texts = formTexts(texts, step),
                // As the frontend, answers stay while the same step is asked again (with its errors)
                values = previous?.values?.takeIf { previous.step.stepId == step.stepId } ?: initialValues(step),
            )
            is LoginStep.Aborted -> LoginUiState.Aborted(server, provider, texts.abort(step), texts.startOver())
            is LoginStep.Done -> {
                _state.value = LoginUiState.SigningIn(server)
                when (val added = addServer(server, step.code)) {
                    is Fetched.Success -> {
                        // Ready for the next login, as this view model outlives the screen (the activity's)
                        _state.value = LoginUiState.ChooseServer()
                        _loggedIn.send(added.value)
                        return
                    }
                    is Fetched.Failure -> LoginUiState.ChooseServer(
                        server.baseUrl(),
                        problem = added.error,
                        manual = true,
                    )
                }
            }
        }
    }

    private companion object {
        val DISCOVERY_STOP_TIMEOUT = 5.seconds.inWholeMilliseconds
        const val ADDRESS = "address"
        const val PROVIDERS = "auth providers"
    }
}

/** Back to the address, filled in, with why it failed, so it can be corrected or tried again. */
private fun chooseServerAgain(server: HttpUrl, problem: LoadError) =
    LoginUiState.ChooseServer(address = server.baseUrl(), problem = problem, manual = true)

private fun formTexts(texts: LoginTexts, step: LoginStep.Form) = FormTexts(
    title = texts.title(step),
    description = texts.description(step),
    labels = step.fields.associate { it.name to texts.label(step, it) },
    errors = step.errors.mapValues { (_, error) -> texts.error(step.handlerType, error) },
    submit = texts.submit(),
)

/** A form's first answers, as the frontend's `computeInitialHaFormData`: blank, off, or a required select's first option. */
private fun initialValues(step: LoginStep.Form): Map<String, JsonPrimitive> = step.fields.mapNotNull { field ->
    when (val type = field.type) {
        LoginFieldType.Text -> field.name to JsonPrimitive("")
        LoginFieldType.Toggle -> field.name to JsonPrimitive(false)
        is LoginFieldType.Select ->
            type.options.firstOrNull()?.takeIf { field.required }?.let { field.name to JsonPrimitive(it.first) }
    }
}.toMap()
