package io.homeassistant.companion.android.dashboard.flow

import io.homeassistant.companion.android.dashboard.entity.Localize

// The texts of a config flow's steps, as `showConfigFlowDialog` gives them to the data entry flow dialog
// (frontend@20260624.6 src/dialogs/config-flow/show-dialog-config-flow.ts).

/**
 * The texts of a form step: its [title], [description], each field's label and help by name, each error's text by
 * field (`base` for the whole form), and the [submit] button's.
 */
data class FlowFormTexts(
    val title: String,
    val description: String?,
    val labels: Map<String, String>,
    val helpers: Map<String, String>,
    val errors: Map<String, String>,
    val submit: String,
)

/**
 * [step]'s texts as the frontend's config flow dialog localizes them: the integration's own (`component.<domain>
 * .config.step.<step>...`, its `title` as the fallback heading), else the field names and error keys.
 */
fun Localize.formTexts(step: FlowStep.Form): FlowFormTexts {
    val domain = step.translationDomain ?: step.handler
    val base = "component.$domain.config.step.${step.stepId}"
    fun text(key: String) = invoke(key, step.placeholders).ifEmpty { null }
    return FlowFormTexts(
        title = text("$base.title") ?: invoke("component.${step.handler}.title").ifEmpty { step.handler },
        description = text("$base.description"),
        labels = step.fields.associate {
            it.name to
                (text("component.${step.handler}.config.step.${step.stepId}.data.${it.name}") ?: it.name)
        },
        helpers = step.fields.mapNotNull { field ->
            text("$base.data_description.${field.name}")?.let { field.name to it }
        }.toMap(),
        errors = step.errors.mapValues { (_, error) -> text("component.$domain.config.error.$error") ?: error },
        submit = text("component.${step.handler}.config.step.${step.stepId}.submit")
            ?: invoke("$FLOW.${if (step.lastStep == false) "next" else "submit"}"),
    )
}

/** What a finished flow says: the integration's own description, and that its config was created, named. */
fun Localize.createdTexts(step: FlowStep.CreateEntry): List<String> {
    val domain = step.translationDomain ?: step.handler
    return listOfNotNull(
        invoke("component.$domain.config.create_entry.${step.description ?: "default"}", step.placeholders).ifEmpty {
            null
        },
        invoke("$FLOW.not_loaded").takeIf { step.notLoaded },
        invoke("$FLOW.created_config", mapOf("name" to step.title)),
    )
}

/** Why the flow ended, as the integration says it, else its reason's key. */
fun Localize.abortText(step: FlowStep.Abort): String =
    invoke("component.${step.translationDomain ?: step.handler}.config.abort.${step.reason}", step.placeholders)
        .ifEmpty { step.reason }

/** The frontend's config flow dialog strings. */
const val FLOW = "ui.panel.config.integrations.config_flow"
