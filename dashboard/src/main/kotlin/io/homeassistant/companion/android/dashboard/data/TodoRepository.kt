package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.dashboard.todo.TodoItem
import io.homeassistant.companion.android.dashboard.todo.moveTodoItemCommand
import io.homeassistant.companion.android.dashboard.todo.parseTodoItems
import io.homeassistant.companion.android.dashboard.todo.subscribeTodoItemsCommand
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/** A to-do list's items as the to-do list card subscribes to them, and moving them. Kept in memory only, while shown. */
class TodoRepository @Inject constructor(
    private val sessions: ServerSessions,
    private val serverManager: ServerManager,
) {
    /** [entityId]'s items, the latest the server sent (`todo/item/subscribe`), subscribed again after a reconnection. */
    fun items(entityId: String): Flow<Loadable<List<TodoItem>>> = sessions.withServer { session ->
        val description = "to-do items of $entityId"
        KeptData<List<TodoItem>>(description, NotKept(), session.connection).subscribed<JsonElement>(
            subscribe = {
                val command = subscribeTodoItemsCommand(entityId)
                session.subscribe(command.type, command.params)
            },
            reduce = { current, event ->
                (event as? JsonObject)?.let(::parseTodoItems)
                    ?: current.also { Timber.w("Ignoring an unexpected $description message") }
            },
        )
    }

    /** Move [uid] after [previousUid] (first without one); `null` when it succeeded, otherwise why it failed. */
    suspend fun move(entityId: String, uid: String, previousUid: String?): LoadError? {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return LoadError.NoServer
        val command = moveTodoItemCommand(entityId, uid, previousUid)
        return (webSocket.request(command.type, command.params) as? Fetched.Failure)?.error
    }
}
