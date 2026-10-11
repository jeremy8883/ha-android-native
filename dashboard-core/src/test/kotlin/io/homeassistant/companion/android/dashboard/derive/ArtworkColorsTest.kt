package io.homeassistant.companion.android.dashboard.derive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Checks [artworkColors] against node-vibrant 4.0.4 with the frontend's generator, run in Node on the same pixels
 * (tools/media-colors/vibrant-harness.cjs, fixtures/media-colors/vibrant.json).
 */
class ArtworkColorsTest {
    @Test
    fun `Given the harness's pixel sets when extracting their colours then they are node-vibrant's`() {
        val expected = Json.parseToJsonElement(
            checkNotNull(javaClass.getResourceAsStream(FIXTURE)).reader().readText(),
        ) as JsonArray
        expected.forEach { entry ->
            val case = entry.jsonObject
            val seed = case.getValue("seed").jsonPrimitive.int
            val colors = checkNotNull(artworkColors(pixels(seed)))
            assertEquals(
                case.getValue("fg").jsonPrimitive.content to case.getValue("bg").jsonPrimitive.content,
                hex(colors.foreground) to hex(colors.background),
                "seed $seed",
            )
        }
    }

    @Test
    fun `Given only white or transparent pixels when extracting colours then there are none`() {
        assertNull(artworkColors(intArrayOf(0xFFFFFFFF.toInt(), 0x00112233)))
    }

    /** The harness's `pixels`: a few base colours with noise, a tenth of the pixels half transparent. */
    private fun pixels(seed: Int): IntArray {
        var state = seed.toLong()
        fun next(): Long {
            state = (state * LCG_A + LCG_C) % MODULUS
            return state
        }
        val k = (2 + next() % 6).toInt()
        val base = List(k) { IntArray(3) { (next() % 256).toInt() } }
        return IntArray(PIXELS) {
            val color = base[(next() % k).toInt()]
            val channels = IntArray(3) { ch -> (color[ch] + (next() % 41).toInt() - 20).coerceIn(0, 255) }
            val alpha = if (next() % 10 == 0L) 100 else 255
            (alpha shl 24) or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
        }
    }

    private fun hex(argb: Int) = "#%06x".format(argb and 0xFFFFFF)

    private companion object {
        const val FIXTURE = "/fixtures/media-colors/vibrant.json"
        const val PIXELS = 3000
        const val LCG_A = 1664525L
        const val LCG_C = 1013904223L
        const val MODULUS = 4294967296L
    }
}
