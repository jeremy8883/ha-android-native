package io.homeassistant.companion.android.dashboard.notification

import io.homeassistant.companion.android.dashboard.json
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PersistentNotificationsTest {
    private val current = json(
        """
        {"type": "current", "notifications": {
          "a": {"notification_id": "a", "title": "Old", "message": "First", "created_at": "2026-10-01T10:00:00+00:00"},
          "b": {"notification_id": "b", "title": null, "message": "Second", "created_at": "2026-10-02T10:00:00+00:00"}
        }}
        """,
    )

    @Test
    fun `Given the current notifications when applied then they are listed newest first`() {
        val notifications = applyNotificationMessage(null, current)!!
        assertEquals(
            listOf(
                PersistentNotification("b", null, "Second", Instant.parse("2026-10-02T10:00:00Z")),
                PersistentNotification("a", "Old", "First", Instant.parse("2026-10-01T10:00:00Z")),
            ),
            sortedNotifications(notifications),
        )
    }

    @Test
    fun `Given a removal when applied then the notification is gone and others stay`() {
        val notifications = applyNotificationMessage(null, current)
        val removed = applyNotificationMessage(
            notifications,
            json("""{"type": "removed", "notifications": {"a": {"notification_id": "a"}}}"""),
        )!!
        assertEquals(setOf("b"), removed.keys)
    }

    @Test
    fun `Given an addition or update when applied then it is merged`() {
        val notifications = applyNotificationMessage(null, current)
        val updated = applyNotificationMessage(
            notifications,
            json("""{"type": "updated", "notifications": {"a": {"notification_id": "a", "message": "Changed"}}}"""),
        )!!
        assertEquals("Changed", updated.getValue("a").message)
        assertEquals(2, updated.size)
    }

    @Test
    fun `Given a message without notifications when applied then it is not one`() {
        assertNull(applyNotificationMessage(emptyMap(), json("""{"type": "added"}""")))
    }
}
