package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.CodeRequest
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.STATE_UNAVAILABLE
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.lightSupportsBrightness
import io.homeassistant.companion.android.dashboard.display.blankBeforeUnit
import io.homeassistant.companion.android.dashboard.display.entityTranslation
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonElement
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

internal fun EntityState.available() = state != STATE_UNAVAILABLE

internal fun entityData(state: EntityState) = buildJsonObject { put("entity_id", state.entityId) }

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

private fun HassSnapshot.lockCommands(state: EntityState): TileFeature? {
    if (state.domain != "lock") return null
    val waiting = state.state in LOCK_WAITING_STATES
    fun can(target: String) = state.available() && (state.assumedState() || (state.state != target && !waiting))
    fun button(service: String, icon: String, target: String) = TileFeature.Button(
        label = localize("ui.card.lock.$service"),
        icon = icon,
        enabled = can(target),
        action = lockCall(state, service),
    )
    return TileFeature.Buttons(
        listOf(button("lock", "mdi:lock", "locked"), button("unlock", "mdi:lock-open-variant", "unlocked")),
    )
}

/**
 * The lock's [service] (lock, unlock or open), asking for a code when the lock has a format for one and no default
 * code saved. Port of `callProtectedLockService` (src/data/lock.ts).
 */
internal fun HassSnapshot.lockCall(state: EntityState, service: String): CardAction.CallService =
    CardAction.CallService(
        "lock",
        service,
        entityData(state),
        target = null,
        code = state.attributes.string("code_format")?.ifEmpty { null }?.let {
            CodeRequest(state.entityId, "lock", codeFormat = TEXT_CODE, title = localize("ui.card.lock.$service"))
        },
    )

/**
 * The translated name of an attribute. Port of `computeAttributeNameDisplay`
 * (src/common/entity/compute_attribute_display.ts).
 */
fun HassSnapshot.attributeName(state: EntityState, attribute: String): String =
    entityTranslation(state, "state_attributes.$attribute.name")
        ?: attribute.replace('_', ' ')
            .replace(Regex("\\bid\\b"), "ID")
            .replace(Regex("\\bip\\b"), "IP")
            .replace(Regex("\\bmac\\b"), "MAC")
            .replace(Regex("\\bgps\\b"), "GPS")
            .replaceFirstChar { it.uppercaseChar() }

internal fun number(value: JsonElement?): Double? =
    (value as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toDoubleOrNull()

internal const val PERCENT = 100.0
private const val BRIGHTNESS_MAX = 255.0
private const val TEXT_CODE = "text"
private val LOCK_WAITING_STATES = setOf("opening", "unlocking", "locking")
