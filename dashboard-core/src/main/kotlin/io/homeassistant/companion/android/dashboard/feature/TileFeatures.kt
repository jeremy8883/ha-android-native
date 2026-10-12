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
import io.homeassistant.companion.android.dashboard.moreinfo.ControlSlider
import io.homeassistant.companion.android.dashboard.moreinfo.MenuOption
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A service call that takes the value the user picks, in [valueKey] of its data, divided by [divisor] (a volume
 * picked in percent is sent as a fraction).
 */
data class ValueService(
    val domain: String,
    val service: String,
    val data: JsonObject,
    val valueKey: String,
    val divisor: Double = 1.0,
) {
    /** The call with [picked] set. */
    fun withValue(picked: Double): CardAction.CallService {
        val value = picked / divisor
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

    /**
     * One button of [Buttons]: an [icon], or a [text] when it has none.
     *
     * @property active whether it is filled with [color] (the state it stands for, when the state is only assumed)
     * @property hiddenWhenNarrow whether it is left out when the tile is narrow and has more than three buttons
     * @property question asked before running, its answer choosing the action instead of [action]
     * @property moreInfo the entity whose details open instead of running [action] (a script missing its fields)
     */
    data class Button(
        val label: String,
        val icon: String?,
        val enabled: Boolean,
        val action: CardAction.CallService,
        val text: String? = null,
        val active: Boolean = false,
        val color: DisplayColor? = null,
        val hiddenWhenNarrow: Boolean = false,
        val question: Question? = null,
        val moreInfo: String? = null,
    )

    /** A yes/no question in a dialog, each answer running its action; dismissing it runs neither. */
    data class Question(
        val title: String,
        val text: String,
        val yesLabel: String,
        val noLabel: String,
        val yes: CardAction.CallService,
        val no: CardAction.CallService,
    )

    /**
     * A dropdown of choices ([ha-control-select-menu]) the width of the tile: the selected option's icon (or [icon]
     * while there is none), the [label] unless [hideLabel], and the selected option.
     *
     * @property optionIcons whether the options show their icons, the selected one's then replacing [icon]
     */
    data class Menu(
        val label: String,
        val icon: String?,
        val value: String?,
        val enabled: Boolean,
        val hideLabel: Boolean,
        val optionIcons: Boolean,
        val options: List<MenuOption>,
    ) : TileFeature

    /** An on/off switch ([ha-control-switch]), sliding to the end when [checked]. */
    data class Switch(
        val label: String,
        val checked: Boolean,
        val enabled: Boolean,
        val onIcon: String,
        val offIcon: String,
        val turnOn: CardAction.CallService,
        val turnOff: CardAction.CallService,
    ) : TileFeature

    /**
     * A slider with a mode and a track of its own ([ha-control-slider]); its colour and a tint without a colour
     * are the feature colour. A [trailing] button (a mute button) can follow it.
     */
    data class ValueSlider(val slider: ControlSlider, val trailing: Button? = null) : TileFeature

    /**
     * The open door button ([hui-lock-open-door-card-feature]): a first press asks to confirm with
     * [confirmLabel], a second one opens and shows [doneLabel] for a moment.
     */
    data class OpenDoor(
        val label: String,
        val confirmLabel: String,
        val doneLabel: String,
        val enabled: Boolean,
        val action: CardAction.CallService,
    ) : TileFeature

    /** A bar filled to [fraction] (0 to 1) of its length in the feature colour ([hui-bar-gauge-card-feature]). */
    data class BarGauge(val fraction: Double) : TileFeature

    /** A button picking a date ([hui-date-set-card-feature]); [date] is the current one (`yyyy-MM-dd`). */
    data class DateSet(val label: String, val enabled: Boolean, val date: LocalDate?, val request: DateRequest) :
        TileFeature

    /**
     * A segmented choice ([ha-control-select]) with icons, or with the options' labels when [showLabels];
     * [selected] is an option value or `null`, and [color] `null` means the feature colour.
     */
    data class Select(
        val label: String,
        val options: List<Option>,
        val selected: String?,
        val enabled: Boolean,
        val color: DisplayColor?,
        val showLabels: Boolean = false,
    ) : TileFeature

    /** One option of [Select]; [action] runs when it is picked. */
    data class Option(val value: String, val label: String, val icon: String?, val action: CardAction.CallService)

    /**
     * Minus/plus buttons around a value ([ha-control-number-buttons]), one per target, then a [trailing] button
     * (a mute button).
     */
    data class NumberButtons(val items: List<NumberItem>, val trailing: Button? = null) : TileFeature

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
        /** [value] moved one [step] in [direction] (-1 or 1), kept within the limits and rounded to the step. */
        fun stepped(value: Double, direction: Int): Double {
            val next = (value + direction * step).coerceIn(
                min ?: Double.NEGATIVE_INFINITY,
                max ?: Double.POSITIVE_INFINITY,
            )
            return java.math.BigDecimal.valueOf(
                next,
            ).setScale(fractionDigits, java.math.RoundingMode.HALF_UP).toDouble()
        }

        /** Whether [value] can go down ([direction] -1) or up (1): it isn't at that limit yet. */
        fun canStep(value: Double, direction: Int): Boolean {
            val limit = if (direction < 0) min else max
            return limit == null || (if (direction < 0) value > limit else value < limit)
        }

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
 * src/panels/lovelace/card-features/. [now] places a trend graph's time window.
 */
fun HassSnapshot.tileFeatures(card: CardConfig, now: Instant? = null): List<TileFeature> {
    val state = card.entity?.let(states::get) ?: return emptyList()
    val color = card.json.string("color")?.ifEmpty { null }
    return card.json.objects("features").mapNotNull { feature ->
        if (feature.string("type") == TREND_GRAPH) {
            trendGraph(state, feature, now?.toEpochMilli()?.toDouble())
        } else {
            tileFeature(state, feature, color)
        }
    }
}

/** The feature [feature] of [state], `null` when its type is unknown or the entity doesn't support it. */
private fun HassSnapshot.tileFeature(state: EntityState, feature: JsonObject, color: String?): TileFeature? =
    FEATURES[feature.string("type")]?.invoke(this, FeatureInput(state, feature, color))

/** What a feature is derived from: the entity, the feature's config and the tile's configured colour. */
private data class FeatureInput(val state: EntityState, val feature: JsonObject, val color: String?)

private typealias FeatureOf = HassSnapshot.(FeatureInput) -> TileFeature?

/** The ported features, by type. */
private val FEATURES: Map<String, FeatureOf> = mapOf(
    "light-brightness" to { lightBrightness(it.state) },
    "light-color-temp" to { lightColorTemp(it.state) },
    "light-color-favorites" to { lightColorFavorites(it.state) },
    "cover-open-close" to { coverOpenClose(it.state) },
    "cover-position" to { positionSlider(it.state.takeIf { s -> s.domain == "cover" }, it.color) },
    "cover-tilt" to { coverTilt(it.state) },
    "cover-tilt-position" to { tiltPositionSlider(it.state, it.color) },
    "cover-position-favorite" to { positionFavorites(it.state, "cover-position-favorite", it.color) },
    "cover-tilt-favorite" to { positionFavorites(it.state, "cover-tilt-favorite", it.color) },
    "valve-open-close" to { valveOpenClose(it.state) },
    "valve-position" to { positionSlider(it.state.takeIf { s -> s.domain == "valve" }, it.color) },
    "valve-position-favorite" to { positionFavorites(it.state, "valve-position-favorite", it.color) },
    "target-temperature" to { targetTemperature(it.state) },
    "target-humidity" to { targetHumidity(it.state) },
    "fan-speed" to { fanSpeed(it.state) },
    "fan-preset-modes" to { fanPresetModes(it.state, it.feature) },
    "fan-direction" to { fanDirection(it.state, it.feature) },
    "fan-oscillate" to { fanOscillate(it.state) },
    "alarm-modes" to { alarmModes(it.state, it.feature) },
    "lock-commands" to { lockCommands(it.state) },
    "lock-open-door" to { lockOpenDoor(it.state) },
    "climate-hvac-modes" to { climateHvacModes(it.state, it.feature) },
    "climate-preset-modes" to { climateAttributeModes(it.state, it.feature, CLIMATE_PRESET_MODES) },
    "climate-fan-modes" to { climateAttributeModes(it.state, it.feature, CLIMATE_FAN_MODES) },
    "climate-swing-modes" to { climateAttributeModes(it.state, it.feature, CLIMATE_SWING_MODES) },
    "climate-swing-horizontal-modes" to {
        climateAttributeModes(it.state, it.feature, CLIMATE_SWING_HORIZONTAL_MODES)
    },
    "humidifier-modes" to { humidifierModes(it.state, it.feature) },
    "humidifier-toggle" to { humidifierToggle(it.state) },
    "water-heater-operation-modes" to { waterHeaterOperationModes(it.state, it.feature) },
    "select-options" to { selectOptions(it.state, it.feature) },
    "numeric-input" to { numericInput(it.state, it.feature) },
    "counter-actions" to { counterActions(it.state, it.feature) },
    "toggle" to { toggleFeature(it.state) },
    "button" to { buttonFeature(it.state, it.feature) },
    "date-set" to { dateSet(it.state) },
    "update-actions" to { updateActions(it.state, it.feature) },
    "vacuum-commands" to { vacuumCommands(it.state, it.feature) },
    "lawn-mower-commands" to { lawnMowerCommands(it.state, it.feature) },
    "bar-gauge" to { barGauge(it.state, it.feature) },
    "media-player-playback" to { mediaPlayerPlayback(it.state, it.feature) },
    "media-player-volume-slider" to { mediaPlayerVolume(it.state, it.feature, buttons = false) },
    "media-player-volume-buttons" to { mediaPlayerVolume(it.state, it.feature, buttons = true) },
    "media-player-source" to { mediaPlayerList(it.state, it.feature, sound = false) },
    "media-player-sound-mode" to { mediaPlayerList(it.state, it.feature, sound = true) },
)

/** The card feature types not ported yet, which draw nothing. */
val UNPORTED_FEATURE_TYPES = setOf("temperature-forecast", "precipitation-forecast")

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
