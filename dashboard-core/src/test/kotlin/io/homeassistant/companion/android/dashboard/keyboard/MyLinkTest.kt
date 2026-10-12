package io.homeassistant.companion.android.dashboard.keyboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MyLinkTest {
    @Test
    fun `Given a page with a My link when creating one then it is the create-link page for its redirect`() {
        assertEquals(MyLink.Create("$CREATE?redirect=history"), createMyLink("/history"))
        assertEquals(MyLink.Create("$CREATE?redirect=energy"), createMyLink("/energy/overview"))
        assertEquals(MyLink.Create("$CREATE?redirect=overview"), createMyLink("/home/overview"))
    }

    @Test
    fun `Given a page without a My link when creating one then none is found`() {
        assertEquals(MyLink.NotFound("/lovelace/0"), createMyLink("/lovelace/0"))
        // Upstream's redirect is /lights, the panel /light
        assertEquals(MyLink.NotFound("/light/0"), createMyLink("/light/0"))
    }

    @Test
    fun `Given a redirect taking parameters when creating a link then the query's are passed on`() {
        assertEquals(
            MyLink.Create("$CREATE?redirect=logs&provider=core"),
            createMyLink("/config/logs", mapOf("provider" to "core", "other" to "x")),
        )
        assertEquals(
            MyLink.Create("$CREATE?redirect=integration&domain=hue"),
            createMyLink("/config/integrations/integration/hue"),
        )
    }

    @Test
    fun `Given redirects sharing a prefix when creating a link then the first in upstream's order wins`() {
        // "developer_services" comes before "developer_call_service" for the same page
        assertEquals(
            MyLink.Create("$CREATE?redirect=developer_services"),
            createMyLink("/config/developer-tools/action", mapOf("service" to "light.turn_on")),
        )
    }

    private companion object {
        const val CREATE = "https://my.home-assistant.io/create-link/"
    }
}
