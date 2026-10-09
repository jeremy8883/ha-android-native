package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Differential tests of the energy sources table against the rows the real frontend's table showed. */
class EnergySourcesTableGoldenTest {

    private val energy = EnergyFixture()

    @TestFactory
    fun `Given a period's data when computing the sources table then it has the frontend's rows`(): List<DynamicTest> = energy.periods.flatMap { recorded ->
        CONFIGS.map { (name, types, onlyTotals) ->
            DynamicTest.dynamicTest("${recorded.name} $name") {
                val expected = recorded.card(name).objects("rows")

                val table = energy.fixture.hass.energySourcesTable(recorded.data, types, onlyTotals)

                val header = listOfNotNull("", "Source") +
                    (if (table.compare) listOfNotNull("Previous usage", "Previous cost".takeIf { table.showCosts }) else emptyList()) +
                    listOfNotNull("Usage", "Cost".takeIf { table.showCosts })
                val actual = listOf(Triple(false, false, header)) + table.rows.map { row ->
                    val cells = listOf("", row.label) +
                        (if (table.compare) listOfNotNull(row.compareEnergy, row.compareCost.takeIf { table.showCosts }) else emptyList()) +
                        listOfNotNull(row.energy, row.cost.takeIf { table.showCosts })
                    Triple(row.total, row.bullet != null, cells)
                }
                assertEquals(
                    expected.map { row ->
                        Triple(
                            row.boolean("total") == true,
                            row.boolean("bullet") == true,
                            (row["cells"] as JsonArray).map { it.stringOrNull.orEmpty() },
                        )
                    },
                    actual,
                )
            }
        }
    }

    private companion object {
        val CONFIGS = listOf(
            Triple("energy-sources-table", null, false),
            Triple("energy-sources-table-totals", null, true),
            Triple("energy-sources-table-electricity", listOf("grid", "solar", "battery"), false),
            Triple("energy-sources-table-gas", listOf("gas"), false),
            Triple("energy-sources-table-water", listOf("water"), false),
        )
    }
}
