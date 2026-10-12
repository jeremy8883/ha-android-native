package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.attributeIcon
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.moreinfo.MenuOption
import io.homeassistant.companion.android.dashboard.moreinfo.hvacModeIcon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of the features built on `HuiModeSelectCardFeatureBase` (frontend@20260624.6
// src/panels/lovelace/card-features/hui-mode-select-card-feature-base.ts): the climate modes, the fan preset and
// direction, the humidifier modes, the water heater operation modes, the select options and the media player's
// source and sound mode. Each is a dropdown, or icons when its style says so.

/** One mode choice: its [value] and how it shows, with an [icon] for the icons style. */
internal data class ModeChoice(val value: String, val label: String, val icon: String?)

/**
 * How a mode feature draws and what it sets: `[domain].[service]` with `{[attribute]: value}`.
 *
 * @property dropdownIcon the dropdown's icon while it shows no option's icon (`_dropdownIconPath`)
 * @property optionIcons whether the dropdown shows the options' icons (`_showDropdownOptionIcons`)
 * @property allowIcons whether the icons style is allowed (`_allowIconsStyle`)
 * @property iconsByDefault whether the icons style is the default (`_defaultStyle`)
 * @property color the icons' colour (`--control-select-color`), `null` for the feature colour
 */
internal data class ModeLook(
    val label: String,
    val attribute: String,
    val domain: String,
    val service: String,
    val dropdownIcon: String?,
    val hideLabel: Boolean = true,
    val optionIcons: Boolean = true,
    val allowIcons: Boolean = true,
    val iconsByDefault: Boolean = false,
    val color: DisplayColor? = null,
)

/** Port of `filterModes`: the [configured] modes the entity supports, in their order, else all [supported]. */
internal fun filterModes(supported: List<String>, configured: List<String>?): List<String> =
    configured?.filter { it in supported } ?: supported

/** The strings of the feature's [key] option, `null` when not configured. */
internal fun JsonObject.configuredList(key: String): List<String>? =
    (this[key] as? JsonArray)?.mapNotNull { it.stringOrNull }

internal fun EntityState.list(key: String): List<String> =
    (attributes[key] as? JsonArray)?.mapNotNull { it.stringOrNull }.orEmpty()

/**
 * The feature as `HuiModeSelectCardFeatureBase.render` draws it: icons (`ha-control-select`) when the style asks
 * for them, else a dropdown (`ha-control-select-menu`).
 */
internal fun modeFeature(
    state: EntityState,
    feature: JsonObject,
    look: ModeLook,
    choices: List<ModeChoice>,
    value: String?,
): TileFeature {
    val style = feature.string("style")
    val icons = look.allowIcons && (style == ICONS || (style == null && look.iconsByDefault))
    fun call(mode: String) = CardAction.CallService(
        look.domain,
        look.service,
        JsonObject(entityData(state) + (look.attribute to JsonPrimitive(mode))),
        target = null,
    )
    return if (icons) {
        TileFeature.Select(
            label = look.label,
            options = choices.map { TileFeature.Option(it.value, it.label, it.icon, call(it.value)) },
            selected = value,
            enabled = state.available(),
            color = look.color,
        )
    } else {
        TileFeature.Menu(
            label = look.label,
            icon = look.dropdownIcon,
            value = value,
            enabled = state.available(),
            hideLabel = look.hideLabel,
            optionIcons = look.optionIcons,
            options = choices.map {
                MenuOption(it.value, it.label, it.icon.takeIf { look.optionIcons }, call(it.value))
            },
        )
    }
}

/** A mode feature reading the modes from [modesAttribute] and the value from [ModeLook.attribute]. */
internal fun HassSnapshot.attributeModes(
    state: EntityState,
    feature: JsonObject,
    look: ModeLook,
    modesAttribute: String,
    configured: List<String>?,
): TileFeature = modeFeature(
    state,
    feature,
    look,
    filterModes(state.list(modesAttribute), configured).map { mode ->
        ModeChoice(
            mode,
            formatEntityAttributeValue(state, look.attribute, JsonPrimitive(mode)),
            attributeIcon(state, look.attribute, mode),
        )
    },
    state.attributes.string(look.attribute),
)

/** Port of `hui-climate-hvac-modes-card-feature`: the modes, in reverse of upstream's order, as icons. */
internal fun HassSnapshot.climateHvacModes(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != CLIMATE) return null
    val ordered = state.list("hvac_modes").sortedBy { HVAC_MODE_ORDER.indexOf(it) }.reversed()
    return modeFeature(
        state,
        feature,
        ModeLook(
            label = localize("ui.card.climate.mode"),
            attribute = "hvac_mode",
            domain = CLIMATE,
            service = "set_hvac_mode",
            dropdownIcon = "mdi:thermostat",
            optionIcons = false,
            iconsByDefault = true,
            color = stateColor(state),
        ),
        filterModes(ordered, feature.configuredList("hvac_modes")).map {
            ModeChoice(it, formatEntityState(state, it), hvacModeIcon(it))
        },
        state.state,
    )
}

/**
 * Ports of `hui-climate-preset-modes-card-feature`, `-fan-modes-`, `-swing-modes-` and
 * `-swing-horizontal-modes-`, for a climate entity supporting [flag].
 */
internal fun HassSnapshot.climateAttributeModes(
    state: EntityState,
    feature: JsonObject,
    spec: ClimateModeSpec,
): TileFeature? {
    if (state.domain != CLIMATE || !state.supportsFeature(spec.flag)) return null
    return attributeModes(
        state,
        feature,
        ModeLook(attributeName(state, spec.attribute), spec.attribute, CLIMATE, spec.service, spec.icon),
        spec.modesAttribute,
        feature.configuredList(spec.configKey),
    )
}

/** What one of the climate's attribute mode features sets, and the entity feature it needs. */
internal data class ClimateModeSpec(
    val flag: Int,
    val attribute: String,
    val modesAttribute: String,
    val configKey: String,
    val service: String,
    val icon: String,
)

internal val CLIMATE_PRESET_MODES =
    ClimateModeSpec(16, "preset_mode", "preset_modes", "preset_modes", "set_preset_mode", "mdi:tune-variant")
internal val CLIMATE_FAN_MODES = ClimateModeSpec(8, "fan_mode", "fan_modes", "fan_modes", "set_fan_mode", "mdi:fan")
internal val CLIMATE_SWING_MODES =
    ClimateModeSpec(32, "swing_mode", "swing_modes", "swing_modes", "set_swing_mode", OSCILLATING_ICON)
internal val CLIMATE_SWING_HORIZONTAL_MODES = ClimateModeSpec(
    512,
    "swing_horizontal_mode",
    "swing_horizontal_modes",
    "swing_horizontal_modes",
    "set_swing_horizontal_mode",
    OSCILLATING_ICON,
)

/** Port of `hui-fan-preset-modes-card-feature`. */
internal fun HassSnapshot.fanPresetModes(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != FAN || !state.supportsFeature(FAN_PRESET_MODE)) return null
    return attributeModes(
        state,
        feature,
        ModeLook(attributeName(state, "preset_mode"), "preset_mode", FAN, "set_preset_mode", "mdi:tune-variant"),
        "preset_modes",
        feature.configuredList("preset_modes"),
    )
}

/** Port of `hui-fan-direction-card-feature`: forward and reverse, as icons by default. */
internal fun HassSnapshot.fanDirection(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != FAN || !state.supportsFeature(FAN_DIRECTION)) return null
    return modeFeature(
        state,
        feature,
        ModeLook(attributeName(state, DIRECTION), DIRECTION, FAN, "set_direction", null, iconsByDefault = true),
        listOf("forward", "reverse").map {
            ModeChoice(it, localize("ui.card.fan.$it"), attributeIcon(state, DIRECTION, it))
        },
        state.attributes.string(DIRECTION),
    )
}

/** Port of `hui-humidifier-modes-card-feature`. */
internal fun HassSnapshot.humidifierModes(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != HUMIDIFIER || !state.supportsFeature(HUMIDIFIER_MODES)) return null
    return attributeModes(
        state,
        feature,
        ModeLook(attributeName(state, "mode"), "mode", HUMIDIFIER, "set_mode", "mdi:tune-variant"),
        "available_modes",
        feature.configuredList("modes"),
    )
}

/** Port of `hui-water-heater-operation-modes-card-feature`: the modes, in reverse of upstream's order, as icons. */
internal fun HassSnapshot.waterHeaterOperationModes(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != WATER_HEATER) return null
    val ordered = state.list("operation_list").sortedBy { WATER_HEATER_ORDER.indexOf(it) }.reversed()
    return modeFeature(
        state,
        feature,
        ModeLook(
            label = localize("ui.card.water_heater.mode"),
            attribute = "operation_mode",
            domain = WATER_HEATER,
            service = "set_operation_mode",
            dropdownIcon = "mdi:water-boiler",
            iconsByDefault = true,
            color = stateColor(state),
        ),
        filterModes(ordered, feature.configuredList("operation_modes")).map {
            ModeChoice(it, formatEntityState(state, it), attributeIcon(state, "operation_mode", it))
        },
        state.state,
    )
}

private const val ICONS = "icons"
private const val CLIMATE = "climate"
private const val FAN = "fan"
private const val HUMIDIFIER = "humidifier"
private const val WATER_HEATER = "water_heater"
private const val DIRECTION = "direction"
private const val OSCILLATING_ICON = "mdi:arrow-oscillating"
private const val FAN_DIRECTION = 4
private const val FAN_PRESET_MODE = 8
private const val HUMIDIFIER_MODES = 1

/** `HVAC_MODES` (src/data/climate.ts), which `compareClimateHvacModes` orders by. */
private val HVAC_MODE_ORDER = listOf("auto", "heat_cool", "heat", "cool", "dry", "fan_only", "off")

/** `OPERATION_MODES` (src/data/water_heater.ts), which `compareWaterHeaterOperationMode` orders by. */
private val WATER_HEATER_ORDER = listOf("electric", "gas", "heat_pump", "eco", "performance", "high_demand", "off")
