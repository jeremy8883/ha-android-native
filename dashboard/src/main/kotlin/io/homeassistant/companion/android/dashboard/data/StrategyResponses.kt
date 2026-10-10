package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.strategy.CommonControls
import io.homeassistant.companion.android.dashboard.strategy.StrategyData
import kotlinx.serialization.json.JsonObject

/** The energy parts of the strategy data; the common controls come from [parseCommonControls]. */
internal fun parseStrategyData(bundle: JsonObject): Fetched<StrategyData> = Fetched.Success(
    StrategyData(
        energyPrefs = bundle[ENERGY_PREFS] as? JsonObject,
        commonControls = CommonControls.NotLoaded,
        energyHiddenCards = (bundle[ENERGY_SETTINGS] as? JsonObject)?.obj("value")?.array("hidden_cards")
            ?.mapNotNull { it.stringOrNull },
    ),
)

/** The `usage_prediction/common_control` entities; an answer without them is unexpected, not "none". */
internal fun parseCommonControls(bundle: JsonObject): Fetched<List<String>> =
    (bundle[COMMON_CONTROLS] as? JsonObject)?.array("entities")?.let { entities ->
        Fetched.Success(entities.mapNotNull { it.stringOrNull })
    } ?: Fetched.Failure(LoadError.UnexpectedResponse("usage_prediction/common_control"))
