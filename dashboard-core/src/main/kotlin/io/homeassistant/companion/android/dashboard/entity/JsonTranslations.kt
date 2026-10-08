package io.homeassistant.companion.android.dashboard.entity

import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * [Localize] over the frontend's nested translation JSON (`{"panel": {"light": "Lights"}}`), resolving dotted
 * keys. Like upstream `computeLocalize` (frontend@20260624.6 src/common/translations/localize.ts), unknown keys
 * give "". ICU message arguments are not supported yet, so messages are returned unformatted.
 */
class JsonTranslations(private val root: JsonObject) : Localize {
    override fun invoke(key: String): String {
        var node: JsonObject = root
        val parts = key.split('.')
        for (part in parts.dropLast(1)) node = node[part] as? JsonObject ?: return ""
        return node[parts.last()]?.stringOrNull.orEmpty()
    }

    companion object {
        /** The bundled frontend translations for [language], extracted by tools/translations/extract_frontend.py. */
        fun bundled(language: String = "en"): JsonTranslations? =
            JsonTranslations::class.java.getResourceAsStream("/translations/frontend-$language.json")
                ?.use { JsonTranslations(Json.parseToJsonElement(it.reader().readText()).jsonObject) }
    }
}
