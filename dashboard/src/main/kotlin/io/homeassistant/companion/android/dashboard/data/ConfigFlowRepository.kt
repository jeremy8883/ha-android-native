package io.homeassistant.companion.android.dashboard.data

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.dashboard.flow.FlowStep
import io.homeassistant.companion.android.dashboard.flow.parseFlowStep
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Config flows and config entries on the active server, as the frontend's data entry flow dialog and the to-do
 * panel's "Delete list" use them (frontend@20260624.6 src/data/config_flow.ts, src/data/config_entries.ts): the
 * REST API, with the session's token.
 */
class ConfigFlowRepository @VisibleForTesting internal constructor(
    private val serverManager: ServerManager,
    private val okHttpClientProvider: SuspendProvider<OkHttpClient>,
    private val io: CoroutineDispatcher,
) {
    @Inject
    constructor(serverManager: ServerManager, okHttpClientProvider: SuspendProvider<OkHttpClient>) :
        this(serverManager, okHttpClientProvider, Dispatchers.IO)

    /** Start [handler]'s flow (`createConfigFlow`): its first step. */
    suspend fun start(handler: String): Fetched<FlowStep> = step(
        "POST",
        FLOW_PATH,
        JsonObject(mapOf("handler" to JsonPrimitive(handler), "show_advanced_options" to JsonPrimitive(false))),
    )

    /** Submit [values] to [flowId]'s step (`handleConfigFlowStep`): the next step, or the same with its errors. */
    suspend fun submit(flowId: String, values: JsonObject): Fetched<FlowStep> =
        step("POST", "$FLOW_PATH/$flowId", values)

    /** Drop the unfinished flow [flowId] (`deleteConfigFlow`), as the dialog does when closed before the end. */
    suspend fun cancel(flowId: String): Fetched<Unit> = call("DELETE", "$FLOW_PATH/$flowId", null).map { }

    /** Remove the config entry [entryId] (`deleteConfigEntry`): whether Home Assistant must restart to finish. */
    suspend fun deleteEntry(entryId: String): Fetched<Boolean> = call("DELETE", "$ENTRY_PATH/$entryId", null)
        .map { (it as? JsonObject)?.boolean("require_restart") == true }

    /**
     * The translations a flow for [handler] shows (`config`, with its `title` as the fallback heading), as the
     * dialog loads them; flat keys.
     */
    suspend fun translations(handler: String, language: String): Fetched<Map<String, String>> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        suspend fun category(name: String) = webSocket.request(
            "frontend/get_translations",
            mapOf("language" to language, "category" to name, "integration" to listOf(handler)),
        ).expect<JsonObject>().map { result ->
            result.obj("resources")?.mapNotNull { (key, value) ->
                value.stringOrNull?.let { key to it }
            }?.toMap().orEmpty()
        }
        return category("config").flatMap { config -> category("title").map { title -> config + title } }
    }

    private suspend fun step(method: String, path: String, body: JsonObject): Fetched<FlowStep> =
        call(method, path, body).flatMap { answer ->
            (answer as? JsonObject)?.let(::parseFlowStep)?.let { Fetched.Success(it) }
                ?: Fetched.Failure(LoadError.UnexpectedResponse(path))
        }

    private suspend fun call(method: String, path: String, body: JsonObject?): Fetched<JsonElement?> = withContext(io) {
        val base = (serverManager.connectionStateProvider().urlFlow().first() as? UrlState.HasUrl)?.url?.toString()
            ?: return@withContext Fetched.Failure(LoadError.NoServer)
        try {
            val request = Request.Builder()
                .url(base.removeSuffix("/") + path)
                .header("Authorization", serverManager.authenticationRepository().buildBearerToken())
                .method(method, body?.toString()?.toRequestBody(JSON))
                .build()
            okHttpClientProvider().newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = text.takeIf {
                    it.isNotBlank()
                }?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
                if (response.isSuccessful) {
                    Fetched.Success(json)
                } else {
                    Timber.w("$method $path failed: ${response.code}")
                    Fetched.Failure(
                        LoadError.Server(code = "${response.code}", message = (json as? JsonObject)?.string("message")),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "$method $path failed")
            Fetched.Failure(LoadError.NoResponse)
        }
    }

    private companion object {
        const val FLOW_PATH = "/api/config/config_entries/flow"
        const val ENTRY_PATH = "/api/config/config_entries/entry"
        val JSON = "application/json".toMediaType()
    }
}
