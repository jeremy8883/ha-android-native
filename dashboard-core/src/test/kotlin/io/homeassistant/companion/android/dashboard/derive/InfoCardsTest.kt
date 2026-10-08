package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.entity.activeRepairsIssues
import io.homeassistant.companion.android.dashboard.entity.applyConfigFlowMessages
import io.homeassistant.companion.android.dashboard.entity.formatIcuMessage
import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.states
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The non-empty paths of the info cards, which the golden test instance has no data for. */
class InfoCardsTest {
    private val strings = mapOf(
        "ui.card.repairs.count_issues" to "{count} {count, plural,\n one {repair}\n other {repairs}\n}",
        "ui.card.discovered-devices.count_devices" to "{count} {count, plural,\n one {device}\n other {devices}\n}",
    )
    private val base = hass(states("{}")).copy(localize = Localize { strings[it].orEmpty() })

    @Test
    fun `Given active, inactive and ignored issues when counting repairs then only active ones count`() {
        val issues = activeRepairsIssues(
            json("""{"issues": [{"issue_id": "a"}, {"issue_id": "b", "active": false}, {"issue_id": "c", "ignored": true}]}"""),
        )
        val hass = base.copy(repairsIssues = issues)
        assertEquals(listOf("a"), issues.map { it.string("issue_id") })
        assertEquals("1 repair", hass.repairsModel(CardConfig(json("""{"type": "repairs"}"""))).secondary)
        assertFalse(hass.cardHidesItself(CardConfig(json("""{"type": "repairs", "hide_empty": true}"""))))
    }

    @Test
    fun `Given flow messages when applying them then snapshots replace, adds append, removes drop and non-discovery is ignored`() {
        fun flow(id: String, source: String) = """{"flow_id": "$id", "context": {"source": "$source"}}"""
        val snapshot = applyConfigFlowMessages(
            null,
            Json.parseToJsonElement("""[{"type": null, "flow": ${flow("1", "zeroconf")}}, {"type": null, "flow": ${flow("2", "user")}}]"""),
        )
        val added = applyConfigFlowMessages(snapshot, Json.parseToJsonElement("""[{"type": "added", "flow": ${flow("3", "dhcp")}}]"""))
        val removed = applyConfigFlowMessages(added, Json.parseToJsonElement("""[{"type": "removed", "flow_id": "1"}]"""))
        assertEquals(listOf("1"), snapshot?.map { it.string("flow_id") })
        assertEquals(listOf("1", "3"), added?.map { it.string("flow_id") })
        assertEquals(listOf("3"), removed?.map { it.string("flow_id") })
        // Not a list of messages, so it can't be applied
        assertEquals(null, applyConfigFlowMessages(added, Json.parseToJsonElement("""{"type": "added"}""")))
        val hass = base.copy(discoveredFlows = added)
        assertEquals("2 devices", hass.discoveredDevicesModel(CardConfig(json("""{"type": "discovered-devices"}"""))).secondary)
    }

    @Test
    fun `Given data still loading when deriving then the card is loading and does not hide for being empty`() {
        val card = CardConfig(json("""{"type": "repairs", "hide_empty": true}"""))
        assertTrue(base.repairsModel(card).loading)
        assertFalse(base.cardHidesItself(card))
        assertTrue(base.copy(user = base.user?.copy(isAdmin = false)).cardHidesItself(card))
        assertEquals("", formatIcuMessage("", emptyMap()))
    }
}
