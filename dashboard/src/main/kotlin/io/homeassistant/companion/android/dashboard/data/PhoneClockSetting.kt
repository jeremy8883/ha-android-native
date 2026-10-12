package io.homeassistant.companion.android.dashboard.data

import android.content.Context
import android.text.format.DateFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The phone's clock setting: whether times show on a 24-hour clock or with AM and PM. */
class PhoneClockSetting @Inject constructor(@ApplicationContext private val context: Context) {
    /** Whether the phone is set to a 24-hour clock, read now so a changed setting applies. */
    fun is24Hour(): Boolean = DateFormat.is24HourFormat(context)
}
