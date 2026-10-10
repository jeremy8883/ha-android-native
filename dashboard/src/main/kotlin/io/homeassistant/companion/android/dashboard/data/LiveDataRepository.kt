package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.common.data.websocket.WebSocketConnectionStatus
import io.homeassistant.companion.android.dashboard.derive.TemplateRequest
import io.homeassistant.companion.android.dashboard.derive.TemplateResult
import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.entity.activeRepairsIssues
import io.homeassistant.companion.android.dashboard.entity.applyConfigFlowMessages
import io.homeassistant.companion.android.dashboard.entity.applyEntityEvent
import io.homeassistant.companion.android.dashboard.model.string
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/**
 * What changes all the time on the active server: entity states, the live collections some cards show, template
 * renderings and camera snapshots, and the connection itself. Subscriptions live only while collected (`:common`
 * restores them after a reconnection) and keep their last value (see [KeptData]).
 */
class LiveDataRepository @Inject constructor(
    private val serverManager: ServerManager,
    private val loadedData: LoadedData,
    private val sessions: ServerSessions,
) {
    /**
     * Signed paths to the latest snapshots of [cameras], refreshed like the frontend's `hui-image`; a camera keeps its
     * last path while a new one can't be signed.
     */
    fun cameraSnapshots(cameras: Set<String>): Flow<Map<String, String>> = flow {
        var paths = emptyMap<String, String>()
        while (true) {
            paths = cameras.mapNotNull { camera ->
                when (val path = cameraSnapshotPath(camera)) {
                    is Fetched.Success -> camera to path.value
                    is Fetched.Failure -> paths[camera]?.let { camera to it }.also {
                        Timber.w("Failed to sign the snapshot path of $camera: ${path.error}")
                    }
                }
            }.toMap()
            emit(paths)
            delay(CAMERA_REFRESH)
        }
    }

    /** The connection's status, or `null` when there is no server. */
    fun connectionStatus(): Flow<WebSocketConnectionStatus?> = flow {
        val webSocket = serverManager.webSocketRepositoryOrNull()
        if (webSocket == null) emit(null) else emitAll(webSocket.connectionStatus())
    }

    /** All entity states, kept up to date through `subscribe_entities`. */
    fun entityStates(): Flow<Loadable<EntityStates>> = sessions.withServer { session ->
        KeptData(SUBSCRIBE_ENTITIES, loadedData.statesKeeper(session.serverId), session.connection).subscribed(
            subscribe = { session.subscribe(SUBSCRIBE_ENTITIES) },
            reduce = { states, event ->
                val message = event as? JsonObject
                when {
                    message == null -> null.also { Timber.w("Ignoring unexpected $SUBSCRIBE_ENTITIES event") }
                    states != null -> applyEntityEvent(states, message)
                    // The first message of a subscription lists every entity; changes applied to nothing would
                    // show no entities, so they wait for it
                    isEntitiesSnapshot(message) -> applyEntityEvent(emptyMap(), message)
                    else -> null.also { Timber.w("Ignoring $SUBSCRIBE_ENTITIES changes before its snapshot") }
                }
            },
        )
    }

    /** Whether [message] is a `subscribe_entities` snapshot: added entities only, no changes or removals. */
    private fun isEntitiesSnapshot(message: JsonObject): Boolean =
        message["a"] is JsonObject && message["c"] == null && message["r"] == null

    /**
     * The active, non-ignored repair issues, loaded again (debounced, like the frontend) when the issue registry
     * changes. Only admins may read them.
     */
    @OptIn(FlowPreview::class)
    fun repairsIssues(): Flow<Loadable<List<JsonObject>>> = sessions.withServer { session ->
        session.parsed(
            name = "repairs",
            refreshes = session.events(REPAIRS_UPDATED_EVENT).debounce(REPAIRS_REFETCH_DEBOUNCE),
            // An answer without its issues is unexpected, never "no repairs"
            parse = { result ->
                if (result["issues"] is JsonArray) {
                    Fetched.Success(activeRepairsIssues(result))
                } else {
                    Fetched.Failure(LoadError.UnexpectedResponse("repairs/list_issues"))
                }
            },
        ) { session.request("repairs/list_issues").expect<JsonObject>() }
    }

    /**
     * Config flows started by discovery, kept up to date through `config_entries/flow/subscribe`. Only admins may
     * subscribe.
     */
    fun discoveredFlows(): Flow<Loadable<List<JsonObject>>> = sessions.withServer { session ->
        val name = "discovered-flows"
        KeptData(name, loadedData.keeper(session.serverId, name, ObjectListCodec), session.connection).subscribed(
            subscribe = { session.subscribe(SUBSCRIBE_CONFIG_FLOWS) },
            reduce = { flows, event -> applyConfigFlowMessages(flows, event) },
        )
    }

    /**
     * The renderings of [request], kept up to date by the server (`render_template`, strict like the markdown
     * card). The last rendering is kept (and cached), so it shows until the server renders it again; a template never
     * rendered has none until the server renders it. Subscribing is retried.
     */
    fun renderTemplate(request: TemplateRequest): Flow<TemplateResult> = flow {
        val serverId = serverManager.getServer()?.id
        val webSocket = serverManager.webSocketRepositoryOrNull()
        if (serverId == null || webSocket == null) {
            // Its source isn't a rendering: it stays unrendered, like a template the server hasn't answered yet
            Timber.w("No server to render a template on")
            return@flow
        }
        val keeper = loadedData.keeper(serverId, "template/${request.cacheKey()}", TextCodec)
        keeper.get()?.value?.let { emit(TemplateResult.Rendered(it)) }
        var failures = 0
        while (true) {
            val events = webSocket.subscribeRaw(RENDER_TEMPLATE, request.params())
            if (events != null) {
                failures = 0
                emitAll(
                    events.mapNotNull { event ->
                        val result = event as? JsonObject ?: return@mapNotNull null
                        result.string("result")?.let { text -> TemplateResult.Rendered(text).also { keeper.put(text) } }
                            ?: result.string("error")?.let { TemplateResult.Failed(it, result.string("level")) }
                    },
                )
            }
            Timber.w("Failed to subscribe to $RENDER_TEMPLATE, or it ended; subscribing again")
            delay(RetryDelays.DEFAULT.after(failures++))
        }
    }

    /**
     * The URL the active server is reached at right now (it changes between home and away networks), without a
     * trailing slash; `null` while there is no safe URL. Server paths such as entity pictures are resolved
     * against it, like the frontend's `hassUrl`.
     */
    fun serverUrl(): Flow<String?> = flow {
        emitAll(
            serverManager.connectionStateProvider().urlFlow().map { state ->
                (state as? UrlState.HasUrl)?.url?.toString()?.removeSuffix("/")
            },
        )
    }

    /**
     * A signed path to the latest snapshot of [cameraEntityId] (`auth/sign_path` for `/api/camera_proxy/...`),
     * as the frontend's `fetchThumbnailUrl` gets it.
     */
    private suspend fun cameraSnapshotPath(cameraEntityId: String): Fetched<String> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        return webSocket.request("auth/sign_path", mapOf("path" to "/api/camera_proxy/$cameraEntityId"))
            .expect<JsonObject>()
            .flatMap { result ->
                result.string("path")?.let { Fetched.Success(it) }
                    ?: Fetched.Failure(LoadError.UnexpectedResponse("auth/sign_path"))
            }
    }
}

private fun TemplateRequest.params(): Map<String, Any?> = buildMap {
    put("template", template)
    entityIds?.let { put("entity_ids", it) }
    put("variables", variables)
    put("strict", true)
}

/** A stable key for this request, for keeping its rendering. */
private fun TemplateRequest.cacheKey(): String = MessageDigest.getInstance("SHA-256")
    .digest(listOf(template, entityIds.toString(), variables.toString()).joinToString("\u0000").toByteArray())
    .joinToString("") { "%02x".format(Locale.ROOT, it) }

/** Caches text as a JSON string. */
private object TextCodec : CacheCodec<String> {
    override fun encode(value: String): String = JsonPrimitive(value).toString()

    override fun decode(json: String): String? = try {
        (Json.parseToJsonElement(json) as? JsonPrimitive)?.takeIf { it.isString }?.content
    } catch (e: SerializationException) {
        Timber.w(e, "Ignoring cached text that can't be parsed")
        null
    }
}

/** As the frontend debounces refetching collections on change events. */
private val REPAIRS_REFETCH_DEBOUNCE = 500.milliseconds

/** How often camera snapshots are refreshed (`UPDATE_INTERVAL` of src/panels/lovelace/components/hui-image.ts). */
private val CAMERA_REFRESH = 10.seconds
private const val SUBSCRIBE_ENTITIES = "subscribe_entities"
private const val SUBSCRIBE_CONFIG_FLOWS = "config_entries/flow/subscribe"
private const val REPAIRS_UPDATED_EVENT = "repairs_issue_registry_updated"
private const val RENDER_TEMPLATE = "render_template"
