package net.jeremycasey.homeassistantnative.login

/** A way of logging in the server offers (`GET /auth/providers`), such as its users' passwords. */
data class AuthProvider(val name: String, val id: String?, val type: String)

/**
 * One step of a login flow (`/auth/login_flow`), as the frontend's login page shows it
 * (frontend@20260624.6 src/auth/ha-auth-flow.ts).
 */
sealed interface LoginStep {
    /**
     * A form to fill in, such as username and password, or a two-factor code.
     *
     * @property handlerType the provider's type, which names the step's texts (`homeassistant`, `trusted_networks`)
     * @property errors the error key of each field that was refused, `base` for the whole form
     * @property placeholders values for the step description, such as the two-factor module's name
     */
    data class Form(
        val flowId: String,
        val handlerType: String,
        val stepId: String,
        val fields: List<LoginField>,
        val errors: Map<String, String>,
        val placeholders: Map<String, String>,
    ) : LoginStep

    /** Logged in: [code] is exchanged for the session's tokens. */
    data class Done(val code: String) : LoginStep

    /** The server ended the flow, for [reason] (such as `login_expired`). */
    data class Aborted(val handlerType: String, val reason: String) : LoginStep
}

/** A field of a login form, from its `data_schema`. */
data class LoginField(val name: String, val required: Boolean, val type: LoginFieldType)

sealed interface LoginFieldType {
    /** Text; secret when named `password`, as the frontend masks it. */
    data object Text : LoginFieldType

    /** One of [options], as (value, label), such as the users trusted networks may log in as. */
    data class Select(val options: List<Pair<String, String>>) : LoginFieldType

    data object Toggle : LoginFieldType
}
