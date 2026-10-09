package io.homeassistant.companion.android.nativedashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.NativeDashboardPaths
import javax.inject.Inject

/** Exposes [NativeDashboardPaths] to the frontend destination. */
@HiltViewModel
internal class NativeDashboardHandOffViewModel @Inject constructor(val paths: NativeDashboardPaths) : ViewModel()
