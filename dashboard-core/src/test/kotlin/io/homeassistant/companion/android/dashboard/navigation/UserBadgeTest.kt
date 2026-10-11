package io.homeassistant.companion.android.dashboard.navigation

import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.states
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserBadgeTest {
    @Test
    fun `Given a name when computing initials then the first letters of up to three words`() {
        assertEquals("JC", userInitials("Jeremy Casey"))
        assertEquals("ABC", userInitials("Ann Bea Cid Dee"))
        assertEquals("D", userInitials("Dev"))
    }

    @Test
    fun `Given no name when computing initials then a question mark`() {
        assertEquals("?", userInitials(""))
    }

    @Test
    fun `Given the user's person has a picture when building the badge then it is used`() {
        val snapshot = hass(
            states(
                """
                {
                  "person.other": {"s": "home", "a": {"user_id": "u2", "entity_picture": "/other.jpg"}},
                  "person.dev": {"s": "home", "a": {"user_id": "u1", "entity_picture": "/dev.jpg"}}
                }
                """,
            ),
        )
        assertEquals(UserBadgeModel("Dev", "D", "/dev.jpg"), snapshot.userBadge())
    }

    @Test
    fun `Given no person for the user when building the badge then initials only`() {
        assertEquals(UserBadgeModel("Dev", "D", null), hass(states("{}")).userBadge())
    }
}
