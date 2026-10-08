package io.homeassistant.companion.android.dashboard.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashboardInfoTest {
    @Test
    fun `Given dashboards list result when parsed then entries are mapped and malformed ones skipped`() {
        val result = Json.parseToJsonElement(
            """
            [
              {"id": "map", "url_path": "map", "title": "Map", "mode": "storage", "require_admin": false, "show_in_sidebar": true},
              {"id": "dashboard_test", "url_path": "dashboard-test", "title": "Test", "mode": "storage", "require_admin": true},
              {"id": "broken"},
              "nonsense"
            ]
            """,
        ).jsonArray
        assertEquals(
            listOf(
                DashboardInfo("map", "Map", "storage", requireAdmin = false),
                DashboardInfo("dashboard-test", "Test", "storage", requireAdmin = true),
            ),
            parseDashboards(result),
        )
    }
}
