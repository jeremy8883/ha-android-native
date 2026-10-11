package io.homeassistant.companion.android.dashboard.notification

import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonObject

/**
 * A persistent notification, as `persistent_notification/subscribe` sends it.
 *
 * @property createdAt when it was created; `null` when the server sent no readable time
 */
data class PersistentNotification(val id: String, val title: String?, val message: String, val createdAt: Instant?)

/** The `persistent_notification/subscribe` command. */
const val SUBSCRIBE_NOTIFICATIONS = "persistent_notification/subscribe"

/**
 * The notifications after the subscription's [message] (`current`, `added`, `updated` or `removed`), applied to
 * [current] (`null` before the first message); `null` when the message isn't one. Port of `NotificationStream`
 * (frontend@20260624.6 src/data/persistent_notification.ts).
 */
fun applyNotificationMessage(
    current: Map<String, PersistentNotification>?,
    message: JsonObject,
): Map<String, PersistentNotification>? {
    val type = message.string("type")
    val notifications = message.obj("notifications")
    val known = current.orEmpty()
    return if (type == null || notifications == null) {
        null
    } else if (type == REMOVED) {
        known - notifications.keys
    } else {
        known +
            notifications.mapNotNull { (id, json) -> (json as? JsonObject)?.let { id to parseNotification(id, it) } }
    }
}

/** The notifications newest first, as the notification drawer lists them. */
fun sortedNotifications(notifications: Map<String, PersistentNotification>): List<PersistentNotification> =
    notifications.values.sortedByDescending { it.createdAt ?: Instant.MIN }

private fun parseNotification(id: String, json: JsonObject) = PersistentNotification(
    id = json.string("notification_id") ?: id,
    title = json.string("title"),
    message = json.string("message").orEmpty(),
    createdAt = json.string("created_at")?.let { parseJsDate(it, ZoneOffset.UTC) },
)

private const val REMOVED = "removed"
