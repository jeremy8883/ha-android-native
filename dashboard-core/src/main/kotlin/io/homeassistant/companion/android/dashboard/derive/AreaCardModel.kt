package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.Gesture
import io.homeassistant.companion.android.dashboard.action.elementActions
import io.homeassistant.companion.android.dashboard.action.hasAction
import io.homeassistant.companion.android.dashboard.entity.AreaEntry
import io.homeassistant.companion.android.dashboard.entity.ENTITY_CATEGORY_NONE
import io.homeassistant.companion.android.dashboard.entity.EntityFilter
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.matches
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Port of `hui-area-card` (frontend@20260624.6 src/panels/lovelace/cards/hui-area-card.ts). The sensors line is in
// AreaSensors.kt, the area controls feature in AreaControls.kt.

/** An area card, or the warning in its place. */
sealed interface AreaCardModel {
    /** The area doesn't exist. */
    data class Warning(val text: String) : AreaCardModel

    /**
     * The area's tile: its [icon] in [color], [name] and [sensors] line, under a [header] (the area's picture, a
     * camera's snapshot or its icon) unless compact, with its [features].
     *
     * @property sensors the sensor classes' values joined with dots, `null` when there are none
     * @property alerts the icons of the first active binary sensor of each alert class: chips on the header, or the
     *   first one as a badge on the icon when compact
     * @property inlineFeatures the (first) feature sits beside the name instead of under it
     * @property actions the tile's gestures (`tap_action`, or a legacy `navigation_path`; none by default)
     * @property imageActions the header's tap, `null` when it doesn't react
     */
    data class Shown(
        val name: String,
        val icon: String,
        val color: DisplayColor,
        val sensors: String?,
        val alerts: List<String>,
        val header: AreaHeader?,
        val vertical: Boolean,
        val inlineFeatures: Boolean,
        val features: List<AreaControls>,
        val actions: ElementActions,
        val imageActions: ElementActions?,
    ) : AreaCardModel
}

/** The picture part of a non-compact area card, [aspectRatio] wide (ignored where the layout sizes the card). */
sealed interface AreaHeader {
    val aspectRatio: Double

    /** A picture: the area's, or a camera's snapshot ([image] `null` until signed). */
    data class Picture(val image: String?, override val aspectRatio: Double) : AreaHeader

    /** The area's [icon] (none without one) on the tile colour, without a picture. */
    data class Icon(val icon: String?, override val aspectRatio: Double) : AreaHeader
}

/** The area card of [card]. Port of `HuiAreaCard.setConfig` and `render`. */
fun HassSnapshot.areaCardModel(card: CardConfig): AreaCardModel {
    val json = card.json
    val area = json.string("area")?.let(registries.areas::get)
        ?: return AreaCardModel.Warning(localize("ui.card.area.area_not_found"))
    val display = areaDisplayType(json)
    val vertical = display == COMPACT && json.boolean("vertical") == true
    val inline = !vertical && json.string("features_position") == INLINE
    val excluded = excludedEntities(json)
    val features = areaControlFeatures(json, area.areaId, excluded).let { if (inline) it.take(1) else it }
    val alerts = activeAlertIcons(json, area.areaId, excluded)
    val camera = areaCamera(area.areaId).takeIf { display == CAMERA }
    return AreaCardModel.Shown(
        name = json.string("name")?.ifEmpty { null } ?: area.name,
        icon = area.icon?.ifEmpty { null } ?: AREA_ICON,
        // The configured colour, else `--tile-color: var(--state-icon-color)`
        color = json.string("color")?.ifEmpty { null }?.let(::cssColor) ?: STATE_ICON_COLOR,
        sensors = areaSensorsLine(json, area, excluded),
        alerts = if (display == COMPACT) alerts.take(1) else alerts,
        header = areaHeader(json, display, area, camera),
        vertical = vertical,
        inlineFeatures = inline,
        features = features,
        actions = elementActions(withTapAction(json)),
        imageActions = imageActions(json, display, camera),
    )
}

/** `display_type`, or `camera` with the legacy `show_camera`, else `picture`. */
private fun areaDisplayType(json: JsonObject): String =
    json.string("display_type") ?: if (json.boolean("show_camera") == true) CAMERA else PICTURE

/** The card's config with its tap action: `tap_action`, else navigating to a legacy `navigation_path`. */
private fun withTapAction(json: JsonObject): JsonObject {
    val path = json.string("navigation_path")
    if (json[TAP_ACTION] != null || path == null) return json
    return JsonObject(json + (TAP_ACTION to navigate(path)))
}

private fun navigate(path: String) = buildJsonObject {
    put("action", "navigate")
    put("navigation_path", path)
}

/**
 * `_hasImageAction` and `_handleImageAction`: the picture runs `image_tap_action` (more-info on the camera by
 * default for a camera), else the card's tap action.
 */
private fun imageActions(json: JsonObject, display: String, camera: String?): ElementActions? {
    val imageTap = json[IMAGE_TAP_ACTION]
        ?: buildJsonObject { put("action", "more-info") }.takeIf { display == CAMERA }
    val card = withTapAction(json)
    val config = when {
        display == COMPACT -> null
        imageTap != null && hasAction(JsonObject(mapOf(TAP_ACTION to imageTap)), Gesture.TAP) ->
            JsonObject(listOfNotNull(camera?.let { ENTITY to JsonPrimitive(it) }, TAP_ACTION to imageTap).toMap())
        hasAction(card, Gesture.TAP) -> card
        else -> null
    }
    return config?.let { ElementActions(it, tap = true, hold = false, doubleTap = false) }
}

private fun HassSnapshot.areaHeader(json: JsonObject, display: String, area: AreaEntry, camera: String?): AreaHeader? {
    if (display == COMPACT) return null
    val ratio = parseAspectRatio(json.string("aspect_ratio")) ?: DEFAULT_ASPECT_RATIO
    val picture = area.json.string("picture")?.ifEmpty { null }
    return when {
        display != PICTURE && display != CAMERA -> AreaHeader.Icon(area.icon?.ifEmpty { null }, ratio)
        // `hui-image` shows the camera's latest signed snapshot over the area's picture
        camera != null -> AreaHeader.Picture(cameraImages[camera], ratio)
        picture != null -> AreaHeader.Picture(picture, ratio)
        else -> AreaHeader.Icon(area.icon?.ifEmpty { null }, ratio)
    }
}

/** `_getCameraEntity`: the area's first camera without an entity category. */
internal fun HassSnapshot.areaCamera(areaId: String): String? = registries.entities.keys.firstOrNull {
    matches(
        it,
        EntityFilter(areas = setOf(areaId), entityCategories = setOf(ENTITY_CATEGORY_NONE), domains = setOf("camera")),
    )
}

/** The cameras whose snapshots the area cards of [cards] show, for the data layer to keep signed. */
internal fun HassSnapshot.areaCameraEntities(cards: List<CardConfig>): Set<String> = cards
    .filter { it.type == AREA && areaDisplayType(it.json) == CAMERA }
    .mapNotNull { card -> card.json.string("area")?.let(::areaCamera) }
    .toSet()

/** `exclude_entities`. */
private fun excludedEntities(json: JsonObject): Set<String> =
    (json["exclude_entities"] as? JsonArray)?.mapNotNull { it.stringOrNull }?.toSet().orEmpty()

/**
 * Port of `_computeActiveAlertStates`: for each of `alert_classes`, the first of the area's binary sensors of that
 * class that is on, as its state icon.
 */
private fun HassSnapshot.activeAlertIcons(json: JsonObject, areaId: String, excluded: Set<String>): List<String> {
    val classes = (json["alert_classes"] as? JsonArray)?.mapNotNull { it.stringOrNull } ?: return emptyList()
    val sensors = areaEntities(areaId, setOf("binary_sensor"), classes.toSet(), excluded)
    return classes.mapNotNull { alertClass ->
        sensors.firstOrNull { id ->
            states[id]?.let { it.attributes.string("device_class") == alertClass && it.state == STATE_ON } == true
        }?.let { entityIcon(it) }
    }
}

/** The area's entities of [domains] (and [deviceClasses]) without an entity category, in registry order. */
internal fun HassSnapshot.areaEntities(
    areaId: String,
    domains: Set<String>,
    deviceClasses: Set<String>?,
    excluded: Set<String>,
): List<String> {
    val filter = EntityFilter(
        areas = setOf(areaId),
        entityCategories = setOf(ENTITY_CATEGORY_NONE),
        domains = domains,
        deviceClasses = deviceClasses,
    )
    return registries.entities.keys.filter { it !in excluded && matches(it, filter) }
}

internal const val AREA = "area"
private const val COMPACT = "compact"
private const val CAMERA = "camera"
private const val PICTURE = "picture"
private const val INLINE = "inline"
private const val ENTITY = "entity"
private const val TAP_ACTION = "tap_action"
private const val IMAGE_TAP_ACTION = "image_tap_action"
private const val STATE_ON = "on"
private const val AREA_ICON = "mdi:texture-box"
private const val DEFAULT_ASPECT_RATIO = 16.0 / 9.0
