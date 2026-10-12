package io.homeassistant.companion.android.dashboard.ui.lovelace.cards.energy

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.dashboard.energy.ENERGY_CSV_FILE
import io.homeassistant.companion.android.dashboard.energy.EnergyData
import io.homeassistant.companion.android.dashboard.energy.energyCsv
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * "Download data" of the energy period selector: the energy data given to the returned function is saved as
 * [ENERGY_CSV_FILE] where the user picks (Android's "save as", standing in for the browser's download), costs in
 * [currency]. When it can't be written, a toast says [errorText].
 *
 * Upstream: `downloadEnergyData` (src/data/energy.ts)
 */
@Composable
internal fun rememberEnergyCsvDownload(currency: String?, errorText: String): (EnergyData) -> Unit {
    val context = LocalContext.current
    val viewModel = hiltViewModel<EnergyCsvViewModel>()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<EnergyData?>(null) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV)) { uri ->
        val data = pending
        pending = null
        if (uri != null && data != null) {
            scope.launch {
                val saved = viewModel.save(data, currency, uri)
                if (!saved) Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
            }
        }
    }
    return { data ->
        pending = data
        // UPSTREAM-DIFF: saves through Android's save-as picker; upstream starts a browser download of the file
        saveAs.launch(ENERGY_CSV_FILE)
    }
}

/** Writes energy data as CSV off the main thread. */
@HiltViewModel
internal class EnergyCsvViewModel @VisibleForTesting constructor(
    private val context: Context,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Dispatchers.IO)

    /** Writes [data] as CSV (costs in [currency]) to [target], a document the user picked; whether it could be. */
    suspend fun save(data: EnergyData, currency: String?, target: Uri): Boolean = withContext(io) {
        try {
            context.contentResolver.openOutputStream(target)?.use {
                it.write(energyCsv(data, currency).toByteArray())
            } !=
                null
        } catch (e: IOException) {
            Timber.w(e, "Couldn't save the energy data")
            false
        }
    }
}

private const val CSV = "text/csv"
