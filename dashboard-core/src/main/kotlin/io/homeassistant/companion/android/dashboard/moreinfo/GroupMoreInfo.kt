package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.derive.WITH_MORE_INFO_DOMAINS
import io.homeassistant.companion.android.dashboard.derive.moreInfoModel
import io.homeassistant.companion.android.dashboard.derive.stateCard
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Port of `more-info-group` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-group.ts) with
 * `computeGroupDomain`: when every member shares a domain with details of its own, those details for the group (the
 * member whose state is the group's, else the first, standing in under the group's name and members), then each
 * member's state card.
 */
internal fun HassSnapshot.groupMoreInfo(state: EntityState, now: Instant): MoreInfoExtra.Group {
    val memberIds = (state.attributes["entity_id"] as? JsonArray).orEmpty().mapNotNull {
        (it as? JsonPrimitive)?.content
    }
    val members = memberIds.mapNotNull(states::get)
    val cards = members.map { stateCard(it, now, inDialog = false) }
    val base = members.firstOrNull { it.state == state.state } ?: members.firstOrNull()
    val domain = memberIds.map { it.substringBefore('.') }.distinct().singleOrNull()
        ?.takeIf { it != GROUP && it in WITH_MORE_INFO_DOMAINS }
    if (base == null || domain == null) {
        return MoreInfoExtra.Group(state.entityId, null, null, cards)
    }
    // Upstream gives the member's state the group's id; here a stand-in id of the members' domain, whose calls the
    // view sends to the group
    val standIn = "$domain.$STAND_IN_PREFIX${state.entityId.substringAfter('.')}"
    val synthetic = base.copy(
        entityId = standIn,
        lastChanged = state.lastChanged,
        lastUpdated = state.lastUpdated,
        attributes = JsonObject(
            base.attributes + listOfNotNull(
                state.attributes["friendly_name"]?.let { "friendly_name" to it },
                state.attributes["entity_id"]?.let { "entity_id" to it },
            ),
        ),
    )
    // UPSTREAM-DIFF: the stand-in is of the members' domain, so its header reads as theirs ("71%"); upstream keeps
    // the group's ID, and shows a light group's brightness raw ("180")
    val inner = copy(states = states + (standIn to synthetic)).moreInfoModel(standIn, now)
    return MoreInfoExtra.Group(state.entityId, inner, synthetic, cards)
}

private const val GROUP = "group"
private const val STAND_IN_PREFIX = "__group_"
