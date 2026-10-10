package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.JsonObject

/** Assist pipelines on the active server, as the frontend's text chat uses them. */
class AssistRepository @Inject constructor(private val serverManager: ServerManager) {
    /** The pipeline [pipelineId] (`assist_pipeline/pipeline/get`), or why it couldn't be loaded. */
    suspend fun pipeline(pipelineId: String): Fetched<JsonObject> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        return webSocket.request(
            "assist_pipeline/pipeline/get",
            mapOf("pipeline_id" to pipelineId),
        ).expect<JsonObject>()
    }

    /** The events of a pipeline run with [params] (`assist_pipeline/run`); fails when it can't start. */
    fun run(params: Map<String, Any?>): Flow<JsonObject> = flow {
        val events = serverManager.webSocketRepositoryOrNull()?.subscribeRaw("assist_pipeline/run", params)
            ?: throw IOException("Couldn't start the pipeline")
        emitAll(events.mapNotNull { it as? JsonObject })
    }
}
