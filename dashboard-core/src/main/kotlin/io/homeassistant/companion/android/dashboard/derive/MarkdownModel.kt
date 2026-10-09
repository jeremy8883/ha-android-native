package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A template the server renders for a card (`render_template`), and keeps rendering as its entities change.
 *
 * @property entityIds the card's `entity_id` option, passed as is
 * @property variables the template variables
 */
data class TemplateRequest(val template: String, val entityIds: JsonElement?, val variables: JsonObject)

/** The latest outcome of a [TemplateRequest]. */
sealed interface TemplateResult {
    /** The rendered text. */
    data class Rendered(val text: String) : TemplateResult

    /** The server reported a problem; [level] is `ERROR` or `WARNING`. */
    data class Failed(val message: String, val level: String?) : TemplateResult
}

/**
 * Display-ready content of a markdown card.
 *
 * @property title the card header, `null` with `text_only`
 * @property content the rendered markdown; `null` while the template has not rendered yet
 * @property error the latest template error to show above the card
 * @property placeholder while there is neither [content] nor [error], the template itself, drawn invisibly so the card
 *   takes about the room it will once rendered and nothing moves when it is
 */
data class MarkdownModel(
    val title: String?,
    val textOnly: Boolean,
    val content: String?,
    val error: String?,
    val placeholder: String? = null,
)

/**
 * The template a markdown card renders, with the variables upstream gives it (`config` and the user's `user`
 * name). Port of `HuiMarkdownCard._tryConnect` (frontend@20260624.6 src/panels/lovelace/cards/hui-markdown-card.ts).
 */
fun HassSnapshot.markdownTemplate(card: CardConfig): TemplateRequest? {
    val content = card.json.string("content")?.takeIf { card.type == MARKDOWN } ?: return null
    return TemplateRequest(
        template = content,
        entityIds = card.json["entity_id"],
        variables = JsonObject(mapOf("config" to card.json, "user" to JsonPrimitive(user?.name.orEmpty()))),
    )
}

/** The templates of the markdown cards among [cards], for the data layer to subscribe to. */
fun HassSnapshot.templateRequests(cards: List<CardConfig>): Set<TemplateRequest> =
    cards.mapNotNull(::markdownTemplate).toSet()

/** Derive a markdown card from its rendered template, or `null` when it is not a markdown card. */
fun HassSnapshot.markdownModel(card: CardConfig): MarkdownModel? {
    val request = markdownTemplate(card) ?: return null
    val result = templates[request]
    val textOnly = card.json.boolean("text_only") == true
    return MarkdownModel(
        title = card.json.string("title")?.ifEmpty { null }.takeUnless { textOnly },
        textOnly = textOnly,
        content = (result as? TemplateResult.Rendered)?.text,
        error = (result as? TemplateResult.Failed)?.message,
        placeholder = request.template.takeIf { result == null },
    )
}

/** Port of the `show_empty: false` rule: an empty rendered result hides the card. */
internal fun HassSnapshot.markdownHidesItself(card: CardConfig): Boolean =
    card.json.boolean("show_empty") == false && markdownModel(card)?.content?.isEmpty() == true

private const val MARKDOWN = "markdown"
