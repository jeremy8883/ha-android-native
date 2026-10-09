package net.jeremycasey.homeassistantnative.login

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.flatMap
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Home Assistant's login API, which its login page is built on (frontend@20260624.6 src/data/auth.ts), so this app
 * logs in natively. It logs in as the companion app's client, which the server allows with its redirect URI
 * (core: homeassistant/components/auth/indieauth.py), so the code it ends with is exchanged the same way.
 */
class LoginFlowApi @VisibleForTesting internal constructor(
    private val apis: HomeAssistantApis,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(apis: HomeAssistantApis) : this(apis, Dispatchers.IO)

    /** The ways of logging in [server] offers. */
    suspend fun providers(server: HttpUrl): Fetched<List<AuthProvider>> =
        call(Request.Builder().url(server.at(PROVIDERS_PATH)).get().build()).flatMap { result ->
            val providers = (result as? JsonObject)?.array("providers")?.filterIsInstance<JsonObject>()
                ?.mapNotNull { provider ->
                    val type = provider.string("type") ?: return@mapNotNull null
                    AuthProvider(name = provider.string("name") ?: type, id = provider.string("id"), type = type)
                }
            providers?.let { Fetched.Success(it) } ?: Fetched.Failure(LoadError.UnexpectedResponse(PROVIDERS_PATH))
        }

    /** Start logging in to [server] with [provider]. */
    suspend fun start(server: HttpUrl, provider: AuthProvider): Fetched<LoginStep> {
        val body = buildJsonObject {
            put("client_id", CLIENT_ID)
            put("handler", JsonArray(listOf(JsonPrimitive(provider.type), JsonPrimitive(provider.id))))
            put("redirect_uri", REDIRECT_URI)
        }
        return call(post(server.at(LOGIN_FLOW_PATH), body)).flatMap(::parseStep)
    }

    /** Answer the form of [flowId] with [values] (by field name). */
    suspend fun submit(server: HttpUrl, flowId: String, values: Map<String, JsonElement>): Fetched<LoginStep> {
        val body = JsonObject(values + ("client_id" to JsonPrimitive(CLIENT_ID)))
        return call(post(server.at("$LOGIN_FLOW_PATH/$flowId"), body)).flatMap(::parseStep)
    }

    private fun post(url: HttpUrl, body: JsonObject): Request = Request.Builder()
        .url(url)
        .post(body.toString().toRequestBody(JSON))
        .build()

    /** The JSON [request] answers with, or why it failed; a refusal carries the server's message. */
    private suspend fun call(request: Request): Fetched<JsonElement> = withContext(ioDispatcher) {
        try {
            apis.getOkHttpClient().newCall(request).execute().use { response ->
                val json = response.body?.string()?.let(::parseJson)
                when {
                    response.isSuccessful && json != null -> Fetched.Success(json)
                    response.isSuccessful -> Fetched.Failure(LoadError.UnexpectedResponse(request.url.encodedPath))
                    else -> Fetched.Failure(
                        LoadError.Server(
                            code = (json as? JsonObject)?.string("code") ?: response.code.toString(),
                            message = (json as? JsonObject)?.string("message"),
                        ),
                    )
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Failed to reach ${request.url.encodedPath}")
            Fetched.Failure(LoadError.NoResponse)
        }
    }

    private fun parseJson(text: String): JsonElement? = try {
        Json.parseToJsonElement(text)
    } catch (e: SerializationException) {
        Timber.w(e, "Login answer isn't JSON")
        null
    }

    private companion object {
        /** The companion app's client (`AuthenticationService.CLIENT_ID`), which the token exchange uses too. */
        const val CLIENT_ID = "https://home-assistant.io/android"
        const val REDIRECT_URI = "homeassistant://auth-callback"
        const val PROVIDERS_PATH = "auth/providers"
        const val LOGIN_FLOW_PATH = "auth/login_flow"
        val JSON = "application/json".toMediaType()
    }
}

/** [path] on this server. */
private fun HttpUrl.at(path: String): HttpUrl = newBuilder().encodedPath("/").addPathSegments(path).build()

/** A login flow result: a form, the code once logged in, or an abort (core: auth/login_flow.py). */
internal fun parseStep(result: JsonElement): Fetched<LoginStep> {
    val step = result as? JsonObject ?: return unexpected("login step")
    val handlerType = (step["handler"] as? JsonArray)?.firstOrNull()?.stringOrNull.orEmpty()
    return when (step.string("type")) {
        "create_entry" -> step.string("result")?.let { Fetched.Success(LoginStep.Done(it)) } ?: unexpected("login code")
        "abort" -> Fetched.Success(LoginStep.Aborted(handlerType, step.string("reason").orEmpty()))
        "form" -> parseForm(step, handlerType)
        else -> unexpected("login step type")
    }
}

private fun parseForm(step: JsonObject, handlerType: String): Fetched<LoginStep> {
    val flowId = step.string("flow_id")
    val fields = (step["data_schema"] as? JsonArray)?.map { (it as? JsonObject)?.let(::parseField) }
    if (flowId == null || fields == null || fields.any { it == null }) return unexpected("login form")
    return Fetched.Success(
        LoginStep.Form(
            flowId = flowId,
            handlerType = handlerType,
            stepId = step.string("step_id").orEmpty(),
            fields = fields.filterNotNull(),
            // No errors and no placeholders are sent as empty or null by design
            errors = step.obj("errors").strings(),
            placeholders = step.obj("description_placeholders").strings(),
        ),
    )
}

/** A `data_schema` field (voluptuous-serialize); `null` for a kind of field this form can't show. */
private fun parseField(field: JsonObject): LoginField? {
    val type = when (field.string("type")) {
        "string" -> LoginFieldType.Text
        "boolean" -> LoginFieldType.Toggle
        "select" -> selectOptions(field["options"])?.let { LoginFieldType.Select(it) }
        else -> null
    }
    val name = field.string("name")
    if (type == null || name == null) {
        Timber.w("Unsupported login field ${field.string("name")} of type ${field.string("type")}")
        return null
    }
    return LoginField(name = name, required = field.boolean("required") == true, type = type)
}

/** Select options as `[[value, label], ...]` or `{value: label}`, both of which voluptuous-serialize sends. */
private fun selectOptions(options: JsonElement?): List<Pair<String, String>>? = when (options) {
    is JsonArray -> options.mapNotNull { option ->
        val pair = option as? JsonArray ?: return@mapNotNull null
        val value = pair.getOrNull(0)?.stringOrNull ?: return@mapNotNull null
        value to (pair.getOrNull(1)?.stringOrNull ?: value)
    }
    is JsonObject -> options.mapNotNull { (value, label) -> label.stringOrNull?.let { value to it } }
    else -> null
}

private fun JsonObject?.strings(): Map<String, String> =
    this?.mapNotNull { (key, value) -> value.stringOrNull?.let { key to it } }?.toMap().orEmpty()

private fun unexpected(what: String) = Fetched.Failure(LoadError.UnexpectedResponse(what))
