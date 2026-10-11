package io.homeassistant.companion.android.dashboard.panels

import io.homeassistant.companion.android.dashboard.target.Target
import io.homeassistant.companion.android.dashboard.target.TargetType
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HistoryLogbookQueryTest {

    @Test
    fun `Given the details' Show more link when opening the panel then it targets that entity from that start`() {
        val query = historyLogbookQuery("entity_id=lock.back_door_lock&start_date=2026-10-09T13%3A00%3A00.000Z&back=1")

        assertEquals(Target(mapOf(TargetType.ENTITY to listOf("lock.back_door_lock"))), query.target)
        assertEquals(Instant.parse("2026-10-09T13:00:00Z"), query.start)
        assertNull(query.end)
    }

    @Test
    fun `Given every kind of target in the address when opening the panel then each is read as a list`() {
        val query = historyLogbookQuery("entity_id=a.b,c.d&device_id=dev&area_id=kitchen&floor_id=up&label_id=red%2Cblue")

        assertEquals(
            Target(
                mapOf(
                    TargetType.ENTITY to listOf("a.b", "c.d"),
                    TargetType.DEVICE to listOf("dev"),
                    TargetType.AREA to listOf("kitchen"),
                    TargetType.FLOOR to listOf("up"),
                    TargetType.LABEL to listOf("red", "blue"),
                ),
            ),
            query.target,
        )
    }

    @Test
    fun `Given no target when opening the panel then it names none`() {
        assertNull(historyLogbookQuery(null).target)
        assertNull(historyLogbookQuery("start_date=2026-10-09T13%3A00%3A00.000Z").target)
    }
}
