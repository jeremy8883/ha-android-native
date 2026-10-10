package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of updates' details against the real frontend's `more-info-update` (20260624.6): updates
 * without install, up to date, with a backup and with release notes, each as it is, off, unavailable, installing
 * (with and without a percentage), skipped, updating automatically and with a summary, with each button's call.
 * The release notes themselves are fetched, so only whether they are is compared.
 */
class UpdateMoreInfoGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured updates when deriving the details then they match the summary and buttons`() = fixture.controlVariants(UPDATE) { hass, state, captured ->
        val main = captured.obj("main")!!
        val update = main.obj("update")!!
        val info = hass.updateMoreInfo(state)
        val expected = info?.let {
            listOf(
                update.string("title"),
                update.objects("rows").map { row -> row.string("key") to (row.string("value") ?: row.string("href")) },
                update.obj("progress")?.let { if (it.boolean("indeterminate") == true) "?" else it.number("value") },
                update.string("backup"),
                main.objects("actions").map { button ->
                    Triple(button.string("text"), button.boolean("disabled") != true, button.boolean("loading") == true)
                },
            )
        }
        val actual = info?.let {
            listOf(
                it.title,
                it.versions + listOfNotNull(it.releaseUrl?.let { url -> it.releaseLabel to url }),
                when (val progress = it.progress) {
                    UpdateProgress.Indeterminate -> "?"
                    is UpdateProgress.Percent -> progress.percent
                    null -> null
                },
                // The test instance has no Supervisor: every update's switch is the generic one
                it.install?.backupType?.let { type -> hass.updateBackupOption(state, type, UpdateBackupSettings(), Instant.EPOCH).title },
                listOf(Triple(it.skip.label, it.skip.enabled, false)) +
                    listOfNotNull(it.install?.let { install -> Triple(install.label, install.enabled, install.installing) }),
            )
        }
        assertEquals(expected, actual)
        // Without a fetch, the summary shows as markdown
        if (info != null && !info.fetchNotes) assertEquals(update.string("markdown")?.ifEmpty { null }, info.summary)
    }

    @TestFactory
    fun `Given captured updates when using each button then the calls match the frontend's`() = fixture.controlVariants(UPDATE) { hass, state, captured ->
        val info = hass.updateMoreInfo(state)
        captured.objects("calls").forEach { call ->
            val index = call.string("label")!!.substringAfterLast(' ').toInt()
            // Upstream asks before skipping an update that installs itself; the details do too
            val actual = if (index == 0) info!!.skip.action else info!!.install!!.call(backup = false)
            assertEquals(call.recordedCalls(), listOf(actual), call.string("label"))
        }
        if (info?.skip?.autoUpdateTitle != null) {
            assertEquals(emptyList<Any>(), captured.objects("calls").filter { it.string("label") == "action 0" })
        }
    }

    private companion object {
        const val UPDATE = "update"
    }
}
