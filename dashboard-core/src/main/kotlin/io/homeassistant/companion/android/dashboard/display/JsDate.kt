package io.homeassistant.companion.android.dashboard.display

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * JavaScript `new Date(text)` for the ISO forms Home Assistant uses: date-only strings are UTC, date-times
 * without an offset are in [zone]. `null` when invalid.
 */
internal fun parseJsDate(text: String, zone: ZoneId): Instant? = runCatching {
    val iso = text.trim().replace(' ', 'T')
    when {
        DATE_ONLY.matches(iso) -> LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant()
        HAS_OFFSET.containsMatchIn(
            iso.substringAfter('T', ""),
        ) -> OffsetDateTime.parse(iso.normalizeOffset()).toInstant()
        else -> LocalDateTime.parse(iso).atZone(zone).toInstant()
    }
}.getOrNull()

/** "+0000" and "+00" offsets as `OffsetDateTime` accepts them. */
private fun String.normalizeOffset(): String = replace(Regex("""([+-]\d{2})(\d{2})$"""), "$1:$2")
    .replace(Regex("""([+-]\d{2})$"""), "$1:00")

private val DATE_ONLY = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val HAS_OFFSET = Regex("""([zZ]|[+-]\d{2}(:?\d{2})?)$""")
