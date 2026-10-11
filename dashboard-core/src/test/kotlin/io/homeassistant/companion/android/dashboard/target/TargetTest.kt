package io.homeassistant.companion.android.dashboard.target

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TargetTest {

    private val kitchen = TargetRef(TargetType.AREA, "kitchen")
    private val lamp = TargetRef(TargetType.ENTITY, "light.lamp")

    @Test
    fun `Given items added and removed when building a target then each is kept once in the order picked`() {
        val target = Target() + kitchen + lamp + lamp + TargetRef(TargetType.ENTITY, "not an id")

        assertEquals(listOf("kitchen"), target[TargetType.AREA])
        assertEquals(listOf("light.lamp"), target[TargetType.ENTITY])
        assertTrue((target - kitchen - lamp).isEmpty)
    }

    @Test
    fun `Given a target when storing it then one id is a string and more a list, as the frontend's`() {
        val target = Target() + kitchen + lamp + TargetRef(TargetType.ENTITY, "light.desk")

        assertEquals(
            JsonObject(
                mapOf(
                    "entity_id" to JsonArray(listOf(JsonPrimitive("light.lamp"), JsonPrimitive("light.desk"))),
                    "area_id" to JsonPrimitive("kitchen"),
                ),
            ),
            target.toJson(),
        )
        assertEquals(target, Target.fromJson(target.toJson()))
    }
}
