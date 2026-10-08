package io.homeassistant.companion.android.dashboard.ui

import android.content.Context
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.data.LoadError

/** What went wrong, for the user. */
internal fun Context.loadErrorText(error: LoadError): String = when (error) {
    LoadError.NoServer -> getString(R.string.native_dashboard_error_no_server)
    LoadError.NoResponse -> getString(R.string.native_dashboard_error_no_response)
    is LoadError.Server -> getString(R.string.native_dashboard_error_server, error.message ?: error.code.orEmpty())
    is LoadError.UnexpectedResponse -> getString(R.string.native_dashboard_error_unexpected, error.command)
}
