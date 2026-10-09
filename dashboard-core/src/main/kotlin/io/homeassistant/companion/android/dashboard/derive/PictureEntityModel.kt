package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.cardActions
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Display-ready content of a picture entity card.
 *
 * @property image the picture's path on the server (or a full URL); for cameras the latest signed snapshot,
 *   `null` until it is signed
 * @property name shown in the footer, `null` with `show_name: false`
 * @property state shown in the footer, `null` with `show_state: false`
 */
data class PictureEntityModel(
    val entityId: String,
    val image: String?,
    val name: String?,
    val state: String?,
    val unavailable: Boolean,
    val actions: ElementActions,
)

/**
 * Derive a picture entity card, or `null` when the entity does not exist. Ports of `HuiPictureEntityCard.render`
 * and the source choice of `hui-image` (frontend@20260624.6 src/panels/lovelace/cards/hui-picture-entity-card.ts,
 * src/panels/lovelace/components/hui-image.ts): a camera shows its snapshot (`camera_view: live` is not ported),
 * otherwise the configured image, an image entity's picture or a person's picture. `state_image` is not ported.
 */
fun HassSnapshot.pictureEntityModel(card: CardConfig): PictureEntityModel? {
    val state = card.entity?.let(states::get) ?: return null
    val entityId = state.entityId
    val camera = cameraOf(card)
    val attributes = state.attributes
    val image = if (camera != null) {
        cameraImages[camera]
    } else {
        (if (card.json.boolean("show_entity_picture") == true) entityPicture(attributes) else null)
            ?: card.json.obj("image")?.string("media_content_id")
            ?: card.json.string("image")
            ?: when (state.domain) {
                "image" -> attributes.string("access_token")?.let {
                    "/api/image_proxy/$entityId?token=$it&state=${state.state}"
                }
                "person" -> attributes.string("entity_picture")
                else -> null
            }
    }
    return PictureEntityModel(
        entityId = entityId,
        image = image?.ifEmpty { null },
        name = entityNameDisplay(state, card.json["name"]).takeIf { card.json.boolean("show_name") != false },
        state = formatEntityState(state).takeIf { card.json.boolean("show_state") != false },
        unavailable = state.state == STATE_UNAVAILABLE,
        // `tap_action` defaults to more-info
        actions = cardActions(CardConfig(JsonObject(card.json + ("type" to JsonPrimitive("tile"))))).card,
    )
}

/** The cameras whose snapshots the [cards] show, for the data layer to keep signed. */
fun HassSnapshot.cameraSnapshotEntities(cards: List<CardConfig>): Set<String> =
    cards.filter { it.type == PICTURE_ENTITY }.mapNotNull(::cameraOf).filter { it in states }.toSet()

private fun cameraOf(card: CardConfig): String? {
    val entity = card.entity
    return if (entity?.substringBefore('.') == "camera") entity else card.json.string("camera_image")?.ifEmpty { null }
}

private fun entityPicture(attributes: JsonObject): String? =
    attributes.string("entity_picture_local")?.ifEmpty { null } ?: attributes.string("entity_picture")?.ifEmpty { null }

private const val PICTURE_ENTITY = "picture-entity"
