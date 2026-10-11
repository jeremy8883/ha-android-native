package io.homeassistant.companion.android.dashboard.display

import io.homeassistant.companion.android.dashboard.model.string
import java.time.DateTimeException
import java.time.ZoneId
import kotlinx.serialization.json.JsonObject

// Port of the frontend's time zone choice (frontend@20260624.6 src/common/datetime/resolve-time-zone.ts,
// src/common/datetime/calc_date.ts and src/common/datetime/check_time.ts), shared by everything that shows or
// computes times.

/** The user's profile setting of which time zone to use (`TimeZone` in src/data/translation.ts). */
sealed interface TimeZoneOption {
    /** The device's time zone: the frontend's default. */
    data object Local : TimeZoneOption

    /** The Home Assistant server's time zone. */
    data object Server : TimeZoneOption

    companion object {
        /**
         * The option the user saved in their profile (`frontend/get_user_data` `language`, its `time_zone`), or the
         * frontend's default [Local] when they never changed it.
         */
        fun fromUserData(language: JsonObject?): TimeZoneOption = when (language?.string("time_zone")) {
            SERVER_VALUE -> Server
            else -> Local
        }
    }
}

/** The time zones the user sees times in and counts days in. */
data class TimeZones(
    /** The time zone dates and times are shown in. */
    val display: ZoneId,
    /** The time zone days start in, for periods, "today" and `time` conditions. */
    val calendar: ZoneId,
)

/**
 * The zones to use for [option], with the server in [serverTimeZone] (`config.time_zone`) and the device in [device].
 *
 * Shown times follow `resolveTimeZone`: the device's zone for [TimeZoneOption.Local], but only when it is one the
 * frontend knows by name (google-timezones-json, plus UTC); otherwise, as for [TimeZoneOption.Server], the server's.
 * Day boundaries follow `calcDate` and `checkTimeInRange`, which use the device's zone unless the option is
 * [TimeZoneOption.Server]. A server zone that is missing or unknown falls back to the device's.
 */
fun resolveTimeZones(option: TimeZoneOption, serverTimeZone: String?, device: ZoneId): TimeZones {
    val server = serverTimeZone?.let(::zoneOrNull) ?: device
    return when (option) {
        TimeZoneOption.Server -> TimeZones(display = server, calendar = server)
        TimeZoneOption.Local -> TimeZones(
            display = if (device.id in RESOLVABLE_TIME_ZONES) device else server,
            calendar = device,
        )
    }
}

private fun zoneOrNull(id: String): ZoneId? = try {
    ZoneId.of(id)
} catch (_: DateTimeException) {
    null
}

private const val SERVER_VALUE = "server"

/**
 * The names the frontend accepts for the browser's zone: UTC and the keys of google-timezones-json 1.2.0 (the list
 * `ha-timezone-picker` offers). A device in another zone, such as Australia/Melbourne, shows times in the server's.
 */
private val RESOLVABLE_TIME_ZONES: Set<String> = setOf(
    "UTC", "Etc/UTC",
    "Pacific/Niue", "Pacific/Pago_Pago", "Pacific/Honolulu", "Pacific/Rarotonga", "Pacific/Tahiti",
    "Pacific/Marquesas", "America/Anchorage", "Pacific/Gambier", "America/Los_Angeles", "America/Tijuana",
    "America/Vancouver", "America/Whitehorse", "Pacific/Pitcairn", "America/Dawson_Creek", "America/Denver",
    "America/Edmonton", "America/Hermosillo", "America/Mazatlan", "America/Phoenix", "America/Yellowknife",
    "America/Belize", "America/Chicago", "America/Costa_Rica", "America/El_Salvador", "America/Guatemala",
    "America/Managua", "America/Mexico_City", "America/Regina", "America/Tegucigalpa", "America/Winnipeg",
    "Pacific/Galapagos", "America/Bogota", "America/Cancun", "America/Cayman", "America/Guayaquil", "America/Havana",
    "America/Iqaluit", "America/Jamaica", "America/Lima", "America/Nassau", "America/New_York", "America/Panama",
    "America/Port-au-Prince", "America/Rio_Branco", "America/Toronto", "Pacific/Easter", "America/Caracas",
    "America/Asuncion", "America/Barbados", "America/Boa_Vista", "America/Campo_Grande", "America/Cuiaba",
    "America/Curacao", "America/Grand_Turk", "America/Guyana", "America/Halifax", "America/La_Paz", "America/Manaus",
    "America/Martinique", "America/Port_of_Spain", "America/Porto_Velho", "America/Puerto_Rico",
    "America/Santo_Domingo", "America/Thule", "Atlantic/Bermuda", "America/St_Johns", "America/Araguaina",
    "America/Argentina/Buenos_Aires", "America/Bahia", "America/Belem", "America/Cayenne", "America/Fortaleza",
    "America/Godthab", "America/Maceio", "America/Miquelon", "America/Montevideo", "America/Paramaribo",
    "America/Recife", "America/Santiago", "America/Sao_Paulo", "Antarctica/Palmer", "Antarctica/Rothera",
    "Atlantic/Stanley", "America/Noronha", "Atlantic/South_Georgia", "America/Scoresbysund", "Atlantic/Azores",
    "Atlantic/Cape_Verde", "Africa/Abidjan", "Africa/Accra", "Africa/Bissau", "Africa/Casablanca", "Africa/El_Aaiun",
    "Africa/Monrovia", "America/Danmarkshavn", "Atlantic/Canary", "Atlantic/Faroe", "Atlantic/Reykjavik", "Etc/GMT",
    "Europe/Dublin", "Europe/Lisbon", "Europe/London", "Africa/Algiers", "Africa/Ceuta", "Africa/Lagos",
    "Africa/Ndjamena", "Africa/Tunis", "Africa/Windhoek", "Europe/Amsterdam", "Europe/Andorra", "Europe/Belgrade",
    "Europe/Berlin", "Europe/Brussels", "Europe/Budapest", "Europe/Copenhagen", "Europe/Gibraltar",
    "Europe/Luxembourg", "Europe/Madrid", "Europe/Malta", "Europe/Monaco", "Europe/Oslo", "Europe/Paris",
    "Europe/Prague", "Europe/Rome", "Europe/Stockholm", "Europe/Tirane", "Europe/Vienna", "Europe/Warsaw",
    "Europe/Zurich", "Africa/Cairo", "Africa/Johannesburg", "Africa/Maputo", "Africa/Tripoli", "Asia/Amman",
    "Asia/Beirut", "Asia/Damascus", "Asia/Gaza", "Asia/Jerusalem", "Asia/Nicosia", "Europe/Athens",
    "Europe/Bucharest", "Europe/Chisinau", "Europe/Helsinki", "Europe/Istanbul", "Europe/Kaliningrad", "Europe/Kyiv",
    "Europe/Riga", "Europe/Sofia", "Europe/Tallinn", "Europe/Vilnius", "Africa/Khartoum", "Africa/Nairobi",
    "Antarctica/Syowa", "Asia/Baghdad", "Asia/Qatar", "Asia/Riyadh", "Europe/Minsk", "Europe/Moscow", "Asia/Tehran",
    "Asia/Baku", "Asia/Dubai", "Asia/Tbilisi", "Asia/Yerevan", "Europe/Samara", "Indian/Mahe", "Indian/Mauritius",
    "Indian/Reunion", "Asia/Kabul", "Antarctica/Mawson", "Asia/Aqtau", "Asia/Aqtobe", "Asia/Ashgabat",
    "Asia/Dushanbe", "Asia/Karachi", "Asia/Tashkent", "Asia/Yekaterinburg", "Indian/Kerguelen", "Indian/Maldives",
    "Asia/Calcutta", "Asia/Colombo", "Asia/Katmandu", "Antarctica/Vostok", "Asia/Almaty", "Asia/Bishkek",
    "Asia/Dhaka", "Asia/Omsk", "Asia/Thimphu", "Indian/Chagos", "Asia/Rangoon", "Indian/Cocos", "Antarctica/Davis",
    "Asia/Bangkok", "Asia/Hovd", "Asia/Jakarta", "Asia/Krasnoyarsk", "Asia/Saigon", "Asia/Ho_Chi_Minh",
    "Indian/Christmas", "Antarctica/Casey", "Asia/Brunei", "Asia/Choibalsan", "Asia/Hong_Kong", "Asia/Irkutsk",
    "Asia/Kuala_Lumpur", "Asia/Macau", "Asia/Makassar", "Asia/Manila", "Asia/Shanghai", "Asia/Singapore",
    "Asia/Taipei", "Asia/Ulaanbaatar", "Australia/Perth", "Asia/Pyongyang", "Asia/Dili", "Asia/Jayapura",
    "Asia/Seoul", "Asia/Tokyo", "Asia/Yakutsk", "Pacific/Palau", "Australia/Adelaide", "Australia/Darwin",
    "Antarctica/DumontDUrville", "Asia/Magadan", "Asia/Vladivostok", "Australia/Brisbane", "Asia/Yuzhno-Sakhalinsk",
    "Australia/Hobart", "Australia/Sydney", "Pacific/Chuuk", "Pacific/Guam", "Pacific/Port_Moresby", "Pacific/Efate",
    "Pacific/Guadalcanal", "Pacific/Kosrae", "Pacific/Norfolk", "Pacific/Noumea", "Pacific/Pohnpei", "Asia/Kamchatka",
    "Pacific/Auckland", "Pacific/Fiji", "Pacific/Funafuti", "Pacific/Kwajalein", "Pacific/Majuro", "Pacific/Nauru",
    "Pacific/Tarawa", "Pacific/Wake", "Pacific/Wallis", "Pacific/Apia", "Pacific/Enderbury", "Pacific/Fakaofo",
    "Pacific/Tongatapu", "Pacific/Kiritimati",
)
