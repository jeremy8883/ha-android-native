package net.jeremycasey.homeassistantnative.login

import io.homeassistant.companion.android.dashboard.entity.Localize

/**
 * The login page's texts, from the frontend's translations (`ui.panel.page-authorize`), named as its login form names
 * them (frontend@20260624.6 src/auth/ha-auth-flow.ts).
 */
internal class LoginTexts(private val localize: Localize) {
    /** The heading: a welcome, or "Just checking" for a second factor. */
    fun title(step: LoginStep.Form): String =
        localize(if (step.stepId in MFA_STEPS) "$PAGE.just_checking" else "$PAGE.welcome_home")

    fun description(step: LoginStep.Form): String? =
        localize("$PROVIDERS.${step.handlerType}.step.${step.stepId}.description", step.placeholders)
            .ifEmpty { null }

    /** The field's label; its name when the frontend has none, as `ha-form` shows it. */
    fun label(step: LoginStep.Form, field: LoginField): String =
        localize("$PROVIDERS.${step.handlerType}.step.${step.stepId}.data.${field.name}").ifEmpty { field.name }

    /** The message for [error] (such as `invalid_auth`), or the frontend's unknown error text. */
    fun error(handlerType: String, error: String): String =
        localize("$PROVIDERS.$handlerType.error.$error").ifEmpty { localize("$PAGE.form.unknown_error") }

    fun abort(step: LoginStep.Aborted): String = listOf(
        localize("$PAGE.abort_intro"),
        localize("$PROVIDERS.${step.handlerType}.abort.${step.reason}").ifEmpty { step.reason },
    ).joinToString(": ")

    fun submit(): String = localize("$PAGE.form.next")

    fun startOver(): String = localize("$PAGE.form.start_over")

    fun pickProvider(): String = localize("$PAGE.pick_auth_provider")

    private companion object {
        const val PAGE = "ui.panel.page-authorize"
        const val PROVIDERS = "$PAGE.form.providers"
        val MFA_STEPS = setOf("select_mfa_module", "mfa")
    }
}
