package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.entity.ENTITY_CATEGORY_NONE
import io.homeassistant.companion.android.dashboard.entity.EntityFilter
import io.homeassistant.companion.android.dashboard.entity.Localize

/**
 * The summaries of the home dashboard, in display order.
 * Port of frontend@20260624.6 src/panels/lovelace/strategies/home/helpers/home-summaries.ts.
 */
enum class HomeSummary(val key: String, val icon: String, val color: String) {
    LIGHT("light", "mdi:lamps", "amber"),
    CLIMATE("climate", "mdi:home-thermometer", "deep-orange"),
    SECURITY("security", "mdi:security", "blue-grey"),
    MEDIA_PLAYERS("media_players", "mdi:multimedia", "blue"),
    MAINTENANCE("maintenance", "mdi:wrench", "grey"),
    ENERGY("energy", "mdi:lightning-bolt", "amber"),
    PERSONS("persons", "mdi:account-multiple", "green"),
    ;

    /** Port of `getSummaryLabel`. */
    fun label(localize: Localize): String = when (this) {
        LIGHT, CLIMATE, SECURITY, MAINTENANCE -> localize("panel.$key")
        else -> localize("ui.panel.lovelace.strategy.home.summary_list.$key")
    }

    /** Port of `HOME_SUMMARIES_FILTERS`. Energy uses energy data instead of entity filters. */
    val filters: List<EntityFilter>
        get() = when (this) {
            LIGHT -> LIGHT_FILTERS
            CLIMATE -> CLIMATE_FILTERS
            SECURITY -> SECURITY_FILTERS
            MEDIA_PLAYERS -> listOf(EntityFilter(domains = setOf("media_player"), entityCategories = NO_CATEGORY))
            MAINTENANCE -> MAINTENANCE_FILTERS
            ENERGY -> emptyList()
            PERSONS -> listOf(EntityFilter(domains = setOf("person")))
        }
}

private val NO_CATEGORY = setOf(ENTITY_CATEGORY_NONE)

/** Port of `lightEntityFilters` (src/panels/light/strategies/light-view-strategy.ts). */
val LIGHT_FILTERS = listOf(EntityFilter(domains = setOf("light"), entityCategories = NO_CATEGORY))

/** Port of `climateEntityFilters` (src/panels/climate/strategies/climate-view-strategy.ts). */
val CLIMATE_FILTERS = listOf(
    EntityFilter(domains = setOf("climate"), entityCategories = NO_CATEGORY),
    EntityFilter(domains = setOf("humidifier"), entityCategories = NO_CATEGORY),
    EntityFilter(domains = setOf("fan"), entityCategories = NO_CATEGORY),
    EntityFilter(domains = setOf("water_heater"), entityCategories = NO_CATEGORY),
    EntityFilter(
        domains = setOf("cover"),
        deviceClasses = setOf("awning", "blind", "curtain", "shade", "shutter", "window", "none"),
        entityCategories = NO_CATEGORY,
    ),
    EntityFilter(domains = setOf("binary_sensor"), deviceClasses = setOf("window"), entityCategories = NO_CATEGORY),
)

/** Port of `securityEntityFilters` (src/panels/security/strategies/security-view-strategy.ts). */
val SECURITY_FILTERS = listOf(
    EntityFilter(domains = setOf("camera"), entityCategories = NO_CATEGORY),
    EntityFilter(domains = setOf("alarm_control_panel"), entityCategories = NO_CATEGORY),
    EntityFilter(domains = setOf("lock"), entityCategories = NO_CATEGORY),
    EntityFilter(
        domains = setOf("cover"),
        deviceClasses = setOf("door", "garage", "gate", "window"),
        entityCategories = NO_CATEGORY,
    ),
    EntityFilter(
        domains = setOf("binary_sensor"),
        deviceClasses = setOf(
            "lock", "door", "window", "garage_door", "opening",
            "carbon_monoxide", "gas", "moisture", "safety", "smoke", "tamper",
        ),
        entityCategories = NO_CATEGORY,
    ),
    // Tamper sensors are wanted even when diagnostic
    EntityFilter(
        domains = setOf("binary_sensor"),
        deviceClasses = setOf("tamper"),
        entityCategories = setOf("diagnostic"),
    ),
)

/** Port of `maintenanceEntityFilters` (src/panels/maintenance/strategies/maintenance-view-strategy.ts). */
val MAINTENANCE_FILTERS = listOf(
    EntityFilter(domains = setOf("sensor"), deviceClasses = setOf("battery")),
    EntityFilter(domains = setOf("binary_sensor"), deviceClasses = setOf("battery")),
)
