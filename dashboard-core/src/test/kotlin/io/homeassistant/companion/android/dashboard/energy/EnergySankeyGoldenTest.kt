package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Differential tests of the energy sankey's nodes, flows and layout input against the frontend's. */
class EnergySankeyGoldenTest {

    private val energy = EnergyFixture()

    @TestFactory
    fun `Given a period's data when computing the energy sankey then it has the frontend's nodes and flows`(): List<DynamicTest> = energy.periods.flatMap { recorded ->
        listOf("energy-sankey" to true, "energy-sankey-flat" to false, "water-sankey" to true).map { (name, grouped) ->
            DynamicTest.dynamicTest("${recorded.name} $name") {
                val expected = recorded.card(name)

                val hass = energy.fixture.hass
                val sankey = if (name.startsWith("water")) {
                    hass.waterSankey(recorded.data, grouped, grouped)
                } else {
                    hass.energySankey(recorded.data, grouped, grouped)
                }

                assertSankey(expected, sankey)
            }
        }
    }
}

/** Asserts [sankey] has the nodes, flows and layout input the frontend's card recorded in [expected]. */
internal fun assertSankey(expected: JsonObject, sankey: SankeyData) {
    val data = expected.obj("data")!!
    assertEquals(
        data.objects("nodes").map { listOf(it.string("id"), it.string("label"), it.number("index")?.toInt()) },
        sankey.nodes.map { listOf(it.id, it.label, it.index) },
    )
    data.objects("nodes").zip(sankey.nodes).forEach { (node, actual) -> assertEquals(node.number("value")!!, actual.value, TOLERANCE) }
    assertEquals(data.objects("links").map { it.string("source") to it.string("target") }, sankey.links.map { it.source to it.target })
    data.objects("links").zip(sankey.links).forEach { (link, actual) ->
        // Flows without a value take what's left of their nodes
        val value = link.number("value")
        if (value == null) assertEquals(null, actual.value) else assertEquals(value, actual.value!!, TOLERANCE)
    }

    val input = sankey.layoutInput()
    val processed = expected.obj("processed")!!
    assertEquals(
        processed.objects("nodes").map { it.string("id") to it.number("depth")?.toInt() },
        input.columns.flatMapIndexed { depth, column -> column.map { it.id to depth } },
    )
    assertEquals(processed.objects("links").map { it.string("source") to it.string("target") }, input.links.map { it.source to it.target })
    processed.objects("links").zip(input.links).forEach { (link, actual) -> assertEquals(link.number("value")!!, actual.value!!, TOLERANCE) }
}

private const val TOLERANCE = 1e-9
