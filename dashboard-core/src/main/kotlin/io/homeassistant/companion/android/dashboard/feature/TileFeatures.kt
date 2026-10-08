package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.CodeRequest
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.EntityFeature
import io.homeassistant.companion.android.dashboard.derive.STATE_UNAVAILABLE
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.lightSupportsBrightness
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.blankBeforeUnit
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A service call that takes the value the user picks, in [valueKey] of its data. */
data class ValueService(val domain: String, val service: String, val data: JsonObject, val valueKey: String) {
    /** The call with [value] set. */
    fun withValue(value: Double): CardAction.CallService {
        val number = if (value == Math.floor(value)) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
        return CardAction.CallService(domain, service, JsonObject(data + (valueKey to number)), target = null)
    }
}

/** A control under a tile, as `hui-card-features` renders one feature. */
sealed interface TileFeature {
    /** A slider ([ha-control-slider]); [value] is `null` when unknown. */
    data class Slider(
        val label: String,
        val value: Int?,
        val min: Int,
        val max: Int,
        val step: Int,
        val unit: String,
        val showHandle: Boolean,
        val enabled: Boolean,
        val service: ValueService,
    ) : TileFeature

    /** A row of buttons ([ha-control-button-group]). */
    data class Buttons(val buttons: List<Button>) : TileFeature

    /** One button of [Buttons]. */
    data class Button(val label: String, val icon: String, val enabled: Boolean, val action: CardAction.CallService)

    /** A segmented choice with icons ([ha-control-select]); [selected] is an option value or `null`. */
    data class Select(
        val label: String,
        val options: List<Option>,
        val selected: String?,
        val enabled: Boolean,
        val color: DisplayColor?,
    ) : TileFeature

    /** One option of [Select]; [action] runs when it is picked. */
    data class Option(val value: String, val label: String, val icon: String, val action: CardAction.CallService)

    /** Minus/plus buttons around a value ([ha-control-number-buttons]), one per target. */
    data class NumberButtons(val items: List<NumberItem>) : TileFeature

    /** One number control; without a [service] (or [value]) it is shown disabled. */
    data class NumberItem(
        val label: String,
        val value: Double?,
        val min: Double?,
        val max: Double?,
        val step: Double,
        val fractionDigits: Int,
        val unit: String?,
        val service: ValueService?,
        val color: DisplayColor?,
    ) {
        /** [value] with [fractionDigits] decimals and the unit, as `ha-control-number-buttons` shows it. */
        fun display(value: Double): String {
            val number = java.math.BigDecimal.valueOf(value)
                .setScale(fractionDigits, java.math.RoundingMode.HALF_UP).toPlainString()
            return if (unit.isNullOrEmpty()) number else number + blankBeforeUnit(unit) + unit
        }
    }
}

/**
 * The features of a tile card that apply to its entity, in order. Unknown feature types and features the
 * entity does not support are skipped, like upstream. Ports of the card features in frontend@20260624.6
 * src/panels/lovelace/card-features/ (light-brightness, cover-open-close, target-temperature, fan-speed,
 * alarm-modes, lock-commands).
 */
fun HassSnapshot.tileFeatures(card: CardConfig): List<TileFeature> {
    val state = card.entity?.let(states::get) ?: return emptyList()
    return card.json.objects("features").mapNotNull { feature ->
        when (feature.string("type")) {
            "light-brightness" -> lightBrightness(state)
            "cover-open-close" -> coverOpenClose(state)
            "target-temperature" -> targetTemperature(state)
            "fan-speed" -> fanSpeed(state)
            "alarm-modes" -> alarmModes(state, feature)
            "lock-commands" -> lockCommands(state)
            else -> null
        }
    }
}

private fun EntityState.available() = state != STATE_UNAVAILABLE

private fun entityData(state: EntityState) = buildJsonObject { put("entity_id", state.entityId) }

private fun HassSnapshot.lightBrightness(state: EntityState): TileFeature? {
    if (state.domain != "light" || !state.lightSupportsBrightness()) return null
    val brightness = number(state.attributes["brightness"])
    return TileFeature.Slider(
        label = localize("ui.card.light.brightness"),
        value = brightness?.let { maxOf(Math.round(it * PERCENT / BRIGHTNESS_MAX).toInt(), 1) },
        min = 1,
        max = PERCENT.toInt(),
        step = 1,
        unit = "%",
        showHandle = state.isActive(),
        enabled = state.available(),
        service = ValueService("light", "turn_on", entityData(state), "brightness_pct"),
    )
}

private fun HassSnapshot.coverOpenClose(state: EntityState): TileFeature? {
    if (state.domain != "cover") return null
    val supportsOpen = state.supportsFeature(EntityFeature.COVER_OPEN)
    val supportsClose = state.supportsFeature(EntityFeature.COVER_CLOSE)
    if (!supportsOpen && !supportsClose) return null
    fun call(service: String) = CardAction.CallService("cover", service, entityData(state), target = null)
    val horizontal = state.attributes.string("device_class") in HORIZONTAL_COVER_CLASSES
    return TileFeature.Buttons(
        listOfNotNull(
            TileFeature.Button(
                label = localize("ui.card.cover.open_cover"),
                icon = if (horizontal) "mdi:arrow-expand-horizontal" else "mdi:arrow-up",
                enabled = canOpenCover(state),
                action = call("open_cover"),
            ).takeIf { supportsOpen },
            TileFeature.Button(
                label = localize("ui.card.cover.stop_cover"),
                icon = "mdi:stop",
                enabled = state.available(),
                action = call("stop_cover"),
            ).takeIf { state.supportsFeature(EntityFeature.COVER_STOP) },
            TileFeature.Button(
                label = localize("ui.card.cover.close_cover"),
                icon = if (horizontal) "mdi:arrow-collapse-horizontal" else "mdi:arrow-down",
                enabled = canCloseCover(state),
                action = call("close_cover"),
            ).takeIf { supportsClose },
        ),
    )
}

/** Ports of `canOpen` and `canClose` (src/data/cover.ts). */
private fun canOpenCover(state: EntityState): Boolean = state.available() &&
    (state.assumedState() || (!coverAt(state, FULLY_OPEN, "open") && state.state != "opening"))

private fun canCloseCover(state: EntityState): Boolean = state.available() &&
    (state.assumedState() || (!coverAt(state, FULLY_CLOSED, "closed") && state.state != "closing"))

/** Ports of `isFullyOpen`/`isFullyClosed`: the position when known, else the state. */
private fun coverAt(state: EntityState, position: Double, stateValue: String): Boolean {
    val current = state.attributes["current_position"] ?: return state.state == stateValue
    return (current as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toDoubleOrNull() == position
}

private fun EntityState.assumedState() = (attributes["assumed_state"] as? JsonPrimitive)?.content == "true"

private fun HassSnapshot.lockCommands(state: EntityState): TileFeature? {
    if (state.domain != "lock") return null
    val waiting = state.state in LOCK_WAITING_STATES
    fun can(target: String) = state.available() && (state.assumedState() || (state.state != target && !waiting))
    fun button(service: String, icon: String, target: String) = TileFeature.Button(
        label = localize("ui.card.lock.$service"),
        icon = icon,
        enabled = can(target),
        action = CardAction.CallService(
            "lock",
            service,
            entityData(state),
            target = null,
            code = state.attributes.string("code_format")?.ifEmpty { null }?.let {
                CodeRequest(state.entityId, "lock", codeFormat = TEXT_CODE, title = localize("ui.card.lock.$service"))
            },
        ),
    )
    return TileFeature.Buttons(
        listOf(button("lock", "mdi:lock", "locked"), button("unlock", "mdi:lock-open-variant", "unlocked")),
    )
}

private fun HassSnapshot.targetTemperature(state: EntityState): TileFeature? {
    val target = (state.domain == "climate" && state.supportsFeature(EntityFeature.CLIMATE_TARGET_TEMPERATURE)) ||
        (state.domain == "water_heater" && state.supportsFeature(EntityFeature.WATER_HEATER_TARGET_TEMPERATURE))
    val range = state.domain == "climate" && state.supportsFeature(EntityFeature.CLIMATE_TARGET_TEMPERATURE_RANGE)
    if (!target && !range) return null

    val attributes = state.attributes
    val step = number(attributes["target_temp_step"])?.takeIf { it != 0.0 }
        ?: if (config.temperatureUnit == UNIT_F) 1.0 else HALF_DEGREE
    val digits = jsNumberString(step).substringAfter('.', "").length
    val min = number(attributes["min_temp"])
    val max = number(attributes["max_temp"])
    val color = stateColor(state)
    fun item(attribute: String, value: Double?, low: Double?, high: Double?, service: ValueService?) =
        TileFeature.NumberItem(
            label = attributeName(state, attribute),
            value = value,
            min = low,
            max = high,
            step = step,
            fractionDigits = digits,
            unit = config.temperatureUnit,
            service = service,
            color = color,
        )
    val available = state.available()
    val temperature = number(attributes["temperature"])
    val low = number(attributes["target_temp_low"])
    val high = number(attributes["target_temp_high"])
    val items = when {
        target && temperature != null && available -> listOf(
            item(
                "temperature",
                temperature,
                min,
                max,
                ValueService(state.domain, "set_temperature", entityData(state), "temperature"),
            ),
        )
        range && low != null && high != null && available -> listOf(
            item(
                "target_temp_low",
                low,
                min,
                minOf(max ?: high, high),
                ValueService(
                    state.domain,
                    "set_temperature",
                    rangeData(state, "target_temp_high", high),
                    "target_temp_low",
                ),
            ),
            item(
                "target_temp_high",
                high,
                maxOf(min ?: low, low),
                max,
                ValueService(
                    state.domain,
                    "set_temperature",
                    rangeData(state, "target_temp_low", low),
                    "target_temp_high",
                ),
            ),
        )
        else -> listOf(item("temperature", null, null, null, null))
    }
    return TileFeature.NumberButtons(items)
}

/** Range changes send both ends, like upstream's `_callService` for `low`/`high`. */
private fun rangeData(state: EntityState, otherKey: String, otherValue: Double) =
    JsonObject(entityData(state) + (otherKey to JsonPrimitive(otherValue)))

private fun HassSnapshot.fanSpeed(state: EntityState): TileFeature? {
    if (state.domain != "fan" || !state.supportsFeature(EntityFeature.FAN_SET_SPEED)) return null
    val step = number(state.attributes["percentage_step"]) ?: 1.0
    val speedCount = Math.round(PERCENT / step).toInt() + 1
    val percentage = if (state.isActive()) number(state.attributes["percentage"]) ?: 0.0 else 0.0
    val label = attributeName(state, "percentage")
    val speeds = FAN_SPEEDS[speedCount]
    if (speeds != null) {
        fun call(percent: Double) =
            ValueService("fan", "set_percentage", entityData(state), "percentage").withValue(percent)
        return TileFeature.Select(
            label = label,
            options = speeds.mapIndexed { index, speed ->
                TileFeature.Option(
                    value = speed,
                    label = if (speed == "on" || speed == "off") {
                        formatEntityState(state, speed)
                    } else {
                        localize("ui.card.fan.speed.$speed").ifEmpty { speed }
                    },
                    icon = fanSpeedIcon(speed, index),
                    action = call(Math.floor(index * step)),
                )
            },
            selected = speeds.getOrElse(Math.round(percentage / step).toInt()) { "off" },
            enabled = state.available(),
            color = null,
        )
    }
    return TileFeature.Slider(
        label = label,
        value = maxOf(Math.round(percentage).toInt(), 0),
        min = 0,
        max = PERCENT.toInt(),
        step = maxOf(step.toInt(), 1),
        unit = "%",
        showHandle = true,
        enabled = state.available(),
        service = ValueService("fan", "set_percentage", entityData(state), "percentage"),
    )
}

/** Port of `computeFanSpeedIcon` (src/data/fan.ts). */
private fun fanSpeedIcon(speed: String, index: Int) = when (speed) {
    "on" -> "mdi:fan"
    "off" -> "mdi:fan-off"
    else -> FAN_SPEED_ICONS.getOrElse(index - 1) { "mdi:fan" }
}

private fun HassSnapshot.alarmModes(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != "alarm_control_panel") return null
    val attributes = state.attributes
    val codeFormat = attributes.string("code_format")?.ifEmpty { null }
    val codeArmRequired = (attributes["code_arm_required"] as? JsonPrimitive)?.content == "true"
    fun call(mode: String): CardAction.CallService {
        val disarm = mode == DISARMED
        val needsCode = codeFormat != null && (disarm || codeArmRequired)
        val title = localize("ui.card.alarm_control_panel.${if (disarm) "disarm" else "arm"}")
        return CardAction.CallService(
            "alarm_control_panel",
            ALARM_MODES.getValue(mode).first,
            entityData(state),
            target = null,
            code = if (needsCode) CodeRequest(state.entityId, "alarm_control_panel", codeFormat, title) else null,
        )
    }
    if (state.state in ALARM_DISARM_ONLY_STATES) {
        return TileFeature.Buttons(
            listOf(
                TileFeature.Button(
                    localize("ui.card.alarm_control_panel.disarm"),
                    "mdi:shield-off",
                    true,
                    call(DISARMED),
                ),
            ),
        )
    }
    val supported = ALARM_MODES.keys.filter { mode ->
        val flag = ALARM_MODES.getValue(mode).second
        flag == null || state.supportsFeature(flag)
    }.reversed()
    val selectedModes = (feature["modes"] as? JsonArray)?.mapNotNull { it.stringOrNull }
    val modes = selectedModes?.filter { it in supported } ?: supported
    return TileFeature.Select(
        label = localize("ui.card.alarm_control_panel.modes_label"),
        options = modes.map { mode ->
            TileFeature.Option(
                mode,
                localize("ui.card.alarm_control_panel.modes.$mode"),
                ALARM_MODE_ICONS.getValue(mode),
                call(mode),
            )
        },
        selected = supported.firstOrNull { it == state.state },
        enabled = state.available(),
        color = stateColor(state),
    )
}

/**
 * The translated name of an attribute. Port of `computeAttributeNameDisplay`
 * (src/common/entity/compute_attribute_display.ts).
 */
fun HassSnapshot.attributeName(state: EntityState, attribute: String): String {
    val entry = registries.entities[state.entityId]
    val deviceClass = state.attributes.string("device_class")
    val domain = state.domain
    return entry?.translationKey?.let {
        localize("component.${entry.platform}.entity.$domain.$it.state_attributes.$attribute.name").ifEmpty { null }
    }
        ?: deviceClass?.ifEmpty { null }?.let {
            localize("component.$domain.entity_component.$it.state_attributes.$attribute.name").ifEmpty { null }
        }
        ?: localize("component.$domain.entity_component._.state_attributes.$attribute.name").ifEmpty { null }
        ?: attribute.replace('_', ' ')
            .replace(Regex("\\bid\\b"), "ID")
            .replace(Regex("\\bip\\b"), "IP")
            .replace(Regex("\\bmac\\b"), "MAC")
            .replace(Regex("\\bgps\\b"), "GPS")
            .replaceFirstChar { it.uppercaseChar() }
}

private fun number(value: kotlinx.serialization.json.JsonElement?): Double? =
    (value as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toDoubleOrNull()

private fun jsNumberString(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else value.toString()

private const val PERCENT = 100.0
private const val BRIGHTNESS_MAX = 255.0
private const val FULLY_OPEN = 100.0
private const val FULLY_CLOSED = 0.0
private const val HALF_DEGREE = 0.5
private const val UNIT_F = "°F"
private const val TEXT_CODE = "text"
private const val DISARMED = "disarmed"
private val HORIZONTAL_COVER_CLASSES = setOf("awning", "door", "gate", "curtain")
private val LOCK_WAITING_STATES = setOf("opening", "unlocking", "locking")
private val ALARM_DISARM_ONLY_STATES = setOf("triggered", "arming", "pending")

/** Port of `FAN_SPEEDS` (src/data/fan.ts): speed names by speed count, for counts up to 4. */
private val FAN_SPEEDS = mapOf(
    2 to listOf("off", "on"),
    3 to listOf("off", "low", "high"),
    4 to listOf("off", "low", "medium", "high"),
)
private val FAN_SPEED_ICONS = listOf("mdi:fan-speed-1", "mdi:fan-speed-2", "mdi:fan-speed-3")

/** Port of `ALARM_MODES` (src/data/alarm_control_panel.ts): mode to service and supported-features flag. */
private val ALARM_MODES: Map<String, Pair<String, Int?>> = linkedMapOf(
    "armed_home" to ("alarm_arm_home" to ALARM_ARM_HOME),
    "armed_away" to ("alarm_arm_away" to ALARM_ARM_AWAY),
    "armed_night" to ("alarm_arm_night" to ALARM_ARM_NIGHT),
    "armed_vacation" to ("alarm_arm_vacation" to ALARM_ARM_VACATION),
    "armed_custom_bypass" to ("alarm_arm_custom_bypass" to ALARM_ARM_CUSTOM_BYPASS),
    DISARMED to ("alarm_disarm" to null),
)
private val ALARM_MODE_ICONS = mapOf(
    "armed_home" to "mdi:home",
    "armed_away" to "mdi:lock",
    "armed_night" to "mdi:moon-waning-crescent",
    "armed_vacation" to "mdi:airplane",
    "armed_custom_bypass" to "mdi:shield",
    DISARMED to "mdi:shield-off",
)

// AlarmControlPanelEntityFeature (src/data/alarm_control_panel.ts)
private const val ALARM_ARM_HOME = 1
private const val ALARM_ARM_AWAY = 2
private const val ALARM_ARM_NIGHT = 4
private const val ALARM_ARM_CUSTOM_BYPASS = 16
private const val ALARM_ARM_VACATION = 32
