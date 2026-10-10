package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Port of `more-info-person` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-person.ts) with
 * `getEntityLocation`: the person's own coordinates, else their first active zone's; and for admins, with their own
 * coordinates, "Create zone from current location".
 */
internal fun HassSnapshot.personMoreInfo(state: EntityState): MoreInfoExtra.Person {
    val own = coordinates(state)
    val location = own ?: firstActiveZone(state)?.let { coordinates(it) }?.copy(accuracy = null)
    // `ha-map`'s marker: the picture, else the first letters of the name's first three words
    val title = state.attributes.string("friendly_name") ?: state.entityId.substringAfter('.')
    return MoreInfoExtra.Person(
        location = location,
        createZone = localize("ui.dialogs.more_info_control.person.create_zone").takeIf {
            own != null &&
                user?.isAdmin == true
        },
        picture = state.attributes.string("entity_picture")?.ifEmpty { null },
        markerLabel = title.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").take(MARKER_LETTERS),
    )
}

private fun coordinates(state: EntityState): MapLocation? {
    val latitude = (state.attributes["latitude"] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    val longitude = (state.attributes["longitude"] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    if (latitude == null || longitude == null) return null
    return MapLocation(latitude, longitude, (state.attributes["gps_accuracy"] as? JsonPrimitive)?.doubleOrNull)
}

/** `findFirstActiveZone`: the first of the person's zones that isn't passive and has coordinates. */
private fun HassSnapshot.firstActiveZone(state: EntityState): EntityState? =
    (state.attributes["in_zones"] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.content?.let(states::get) }
        .firstOrNull { zone -> !jsTruthy(zone.attributes["passive"]) && coordinates(zone) != null }

private const val MARKER_LETTERS = 3
