package io.homeassistant.companion.android.dashboard.display

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimeZonesTest {

    @Test
    fun `Given no saved setting when reading the option then it is the frontend's default, local`() {
        assertEquals(TimeZoneOption.Local, TimeZoneOption.fromUserData(null))
        assertEquals(TimeZoneOption.Local, TimeZoneOption.fromUserData(buildJsonObject { put("language", JsonPrimitive("en")) }))
    }

    @Test
    fun `Given the server setting saved when reading the option then it is server`() {
        val data = buildJsonObject { put("time_zone", JsonPrimitive("server")) }

        assertEquals(TimeZoneOption.Server, TimeZoneOption.fromUserData(data))
    }

    @Test
    fun `Given local and a device zone the frontend knows when resolving then times show in the device's zone`() {
        val zones = resolveTimeZones(TimeZoneOption.Local, AMSTERDAM, ZoneId.of("Australia/Sydney"))

        assertEquals(TimeZones(display = ZoneId.of("Australia/Sydney"), calendar = ZoneId.of("Australia/Sydney")), zones)
    }

    @Test
    fun `Given local and a device zone missing from the frontend's list when resolving then times show in the server's zone but days start in the device's`() {
        val melbourne = ZoneId.of("Australia/Melbourne")

        val zones = resolveTimeZones(TimeZoneOption.Local, AMSTERDAM, melbourne)

        assertEquals(TimeZones(display = ZoneId.of(AMSTERDAM), calendar = melbourne), zones)
        // What the frontend showed on an emulator in Melbourne for a change at 00:25 UTC
        val changed = Instant.parse("2026-10-11T00:25:47Z")
        assertEquals("October 11, 2026 at 2:25:47 AM", JdkDisplayFormats.DEFAULT.dateTimeWithSeconds(changed, zones.display))
    }

    @Test
    fun `Given UTC on the device when resolving local then times show in UTC`() {
        val zones = resolveTimeZones(TimeZoneOption.Local, AMSTERDAM, ZoneId.of("UTC"))

        assertEquals(ZoneId.of("UTC"), zones.display)
    }

    @Test
    fun `Given the server setting when resolving then both zones are the server's`() {
        val zones = resolveTimeZones(TimeZoneOption.Server, AMSTERDAM, ZoneId.of("Australia/Sydney"))

        assertEquals(TimeZones(display = ZoneId.of(AMSTERDAM), calendar = ZoneId.of(AMSTERDAM)), zones)
    }

    @Test
    fun `Given an unknown or missing server zone when resolving then the device's zone is used`() {
        val melbourne = ZoneId.of("Australia/Melbourne")

        assertEquals(TimeZones(melbourne, melbourne), resolveTimeZones(TimeZoneOption.Server, "Nowhere/Land", melbourne))
        assertEquals(TimeZones(melbourne, melbourne), resolveTimeZones(TimeZoneOption.Local, null, melbourne))
    }

    private companion object {
        const val AMSTERDAM = "Europe/Amsterdam"
    }
}
