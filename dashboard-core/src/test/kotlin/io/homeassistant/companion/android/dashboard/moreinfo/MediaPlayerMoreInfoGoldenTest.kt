package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.derive.MediaControl
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of media players' details against the real frontend's `more-info-media_player`
 * (20260624.6): a TV, a speaker, a TV with sources, a player that only browses and a group, each as it is, off,
 * unavailable, paused, idle, muted (with repeat and shuffle on), only assumed, and playing at a fixed time, with
 * each control's call. The media browser and the grouping dialog aren't ported, so their buttons are left out.
 */
class MediaPlayerMoreInfoGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured players when deriving the artwork and titles then they match`() = each { info, captured, _ ->
        val media = captured.media()
        val cover = media.obj("cover")
        val empty = media.obj("emptyCover")
        assertEquals(empty?.string("text"), info.unavailable)
        if (info.unavailable == null) {
            assertEquals(cover != null, info.picture != null)
            cover?.let { assertTrue(it.string("src")!!.endsWith(info.picture!!), "${it.string("src")} ${info.picture}") }
            assertEquals(cover?.boolean("playing") ?: false, info.picture != null && info.playing)
            assertEquals(media.string("title"), info.title)
            assertEquals(media.string("artist"), info.artist)
        }
    }

    @TestFactory
    fun `Given captured players when deriving the position then it matches the position slider`() = each { info, captured, _ ->
        val media = captured.media()
        val now = Instant.ofEpochMilli(media.number("capturedAt")!!.toLong())
        val expected = media.obj("position")?.let {
            listOf(it.number("max"), it.number("value"), it.boolean("disabled") != true)
        }
        val position = info.position.takeIf { info.unavailable == null }
        // The capture reads its clock a moment after drawing the slider
        val actual = position?.let { listOf(it.duration, it.sliderAt(now), it.enabled) }
        if (expected != null && actual != null && position.ticking) {
            assertEquals(expected[0], actual[0])
            assertTrue(Math.abs((expected[1] as Double) - (actual[1] as Double)) <= 1.0, "$expected $actual")
            assertEquals(expected[2], actual[2])
        } else {
            assertEquals(expected, actual)
        }
    }

    @TestFactory
    fun `Given captured players when deriving the transport buttons then they match the main controls`() = each { info, captured, _ ->
        val expected = captured.media().array("main")?.map { slot ->
            (slot as? JsonObject)?.let { it.string("action") to it.string("label") }
        }
        val main = info.main.takeIf { info.unavailable == null }
        val actual = main?.let { (it.left + it.center + it.right).map { control -> control?.pair() } }
        assertEquals(expected, actual)
    }

    @TestFactory
    fun `Given captured players when deriving the volume then it matches the volume row`() = each { info, captured, _ ->
        val expected = captured.media().obj("volume")?.let { volume ->
            listOf(
                volume.objects("buttons").map { it.string("action") to it.string("label") },
                volume.number("slider"),
                volume.boolean("icon"),
            )
        }
        val actual = info.volume.takeIf { info.unavailable == null }?.let { volume ->
            listOf(
                listOfNotNull(
                    volume.mute?.let { null to it.label },
                    volume.down?.pair(),
                    volume.up?.pair(),
                ),
                volume.level,
                volume.slider != null && volume.mute == null,
            )
        }
        assertEquals(expected, actual)
    }

    @TestFactory
    fun `Given captured players when deriving the controls row then it matches, without browse and grouping`() = each { info, captured, _ ->
        val media = captured.media()
        val expected = media.array("row")!!.map { it.stringOrNull }.filter { it !in NOT_PORTED }
        val actual = if (info.unavailable != null) {
            emptyList()
        } else {
            listOfNotNull(
                info.source?.let { "source-button" },
                info.soundMode?.let { "sound-mode-button" },
                info.turnOn?.let { "media-control-row-button-turn_on" },
                info.turnOff?.let { "media-control-row-button-turn_off" },
            )
        }
        assertEquals(expected, actual)
        val shown = info.takeIf { it.unavailable == null }
        assertEquals(media.dropdown("sources"), shown?.source?.items())
        assertEquals(media.dropdown("soundModes"), shown?.soundMode?.items())
    }

    @TestFactory
    fun `Given captured players when using each control then the calls match the frontend's`() = each { info, captured, _ ->
        captured.objects("calls").forEach { call ->
            val label = call.string("label")!!
            val value = label.substringAfterLast(' ')
            val actual = when (call.string("control")) {
                "button" -> info.allControls().first { it.action.service == label }.action
                "mute" -> info.volume!!.mute!!.action
                "volume" -> info.volume!!.setTo(value.toDouble())!!
                "seek" -> info.position!!.seek.withValue(value.toDouble())
                "source" -> info.source!!.options.single { it.value == value }.action
                else -> info.soundMode!!.options.single { it.value == value }.action
            }
            assertEquals(call.recordedCalls(), listOf(actual), label)
        }
    }

    private fun MediaControl.pair() = action.service to label

    private fun MediaPlayerMoreInfo.allControls(): List<MediaControl> = listOfNotNull(turnOn, turnOff, volume?.down, volume?.up) + main?.let { it.left + it.center + it.right }.orEmpty().filterNotNull()

    private fun SelectMenu.items() = options.map { Triple(it.value, it.label, it.value == value) }

    private fun JsonObject.media(): JsonObject = obj("main")!!.obj("media")!!

    private fun JsonObject.dropdown(key: String) = (this[key]?.takeIf { it !is JsonNull })?.let {
        objects(key).map { item -> Triple(item.string("value"), item.string("label"), item.boolean("selected") == true) }
    }

    private fun each(check: (MediaPlayerMoreInfo, JsonObject, String) -> Unit) = fixture.controlVariants(MEDIA_PLAYER) { hass, state, captured ->
        check(hass.mediaPlayerMoreInfo(state)!!, captured, state.entityId)
    }

    private companion object {
        val NOT_PORTED = setOf("media-control-row-button-browse_media", "grouping-button")
    }
}
