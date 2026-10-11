package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.notification.PersistentNotification
import io.homeassistant.companion.android.dashboard.notification.SUBSCRIBE_NOTIFICATIONS
import io.homeassistant.companion.android.dashboard.notification.applyNotificationMessage
import io.homeassistant.companion.android.dashboard.notification.sortedNotifications
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/** The server's persistent notifications, as the sidebar and the notification drawer show them, and dismissing them. */
class NotificationsRepository @Inject constructor(
    private val sessions: ServerSessions,
    private val actions: ServerActionsRepository,
) {
    /**
     * The notifications, newest first, kept up to date through `persistent_notification/subscribe` and subscribed
     * again after a reconnection. Kept in memory only.
     */
    fun notifications(): Flow<Loadable<List<PersistentNotification>>> = sessions.withServer { session ->
        KeptData<Map<String, PersistentNotification>>(SUBSCRIBE_NOTIFICATIONS, NotKept(), session.connection)
            .subscribed<JsonElement>(
                subscribe = { session.subscribe(SUBSCRIBE_NOTIFICATIONS) },
                reduce = { current, event ->
                    (event as? JsonObject)?.let { applyNotificationMessage(current, it) }
                        ?: current.also { Timber.w("Ignoring an unexpected $SUBSCRIBE_NOTIFICATIONS message") }
                },
            )
            .map { loadable -> loadable.map(::sortedNotifications) }
    }

    /** Dismiss the notification [id]; `null` when it succeeded, otherwise why it failed. */
    suspend fun dismiss(id: String): LoadError? = actions.callService(
        domain = DOMAIN,
        service = "dismiss",
        data = JsonObject(mapOf("notification_id" to JsonPrimitive(id))),
        target = null,
    )

    /** Dismiss every notification; `null` when it succeeded, otherwise why it failed. */
    suspend fun dismissAll(): LoadError? = actions.callService(DOMAIN, "dismiss_all", data = null, target = null)

    private companion object {
        const val DOMAIN = "persistent_notification"
    }
}
