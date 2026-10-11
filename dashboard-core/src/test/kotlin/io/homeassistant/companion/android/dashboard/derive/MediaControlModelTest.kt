package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaControlModelTest {
    private val hass = GoldenFixture("test-instance").hass

    private fun media(entity: String) = checkNotNull(hass.mediaControlModel(CardConfig(json("""{"type": "media-control", "entity": "$entity"}"""))))

    @Test
    fun `Given a playing player with a position when deriving its card then the progress bar and the pause button show`() {
        val model = media("media_player.living_room")
        assertNotNull(model.progress)
        assertTrue(model.controls.single { it.action.service == "media_pause" }.main)
        assertFalse(model.controls.single { it.action.service == "turn_off" }.main)
    }

    @Test
    fun `Given players that browse or group when deriving their cards then the matching buttons show`() {
        assertNotNull(media("media_player.browse").browseLabel)
        assertNull(media("media_player.browse").grouping)
        assertNotNull(media("media_player.walkman").grouping)
    }

    @Test
    fun `Given a player without a position when deriving its card then there is no progress bar`() {
        assertNull(media("media_player.walkman").progress)
        assertEquals(false, media("media_player.walkman").unavailable)
    }
}
