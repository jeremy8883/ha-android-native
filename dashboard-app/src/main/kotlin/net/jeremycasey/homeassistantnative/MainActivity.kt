package net.jeremycasey.homeassistantnative

import android.os.Build
import android.os.Bundle
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.dashboard.ui.dialogs.quickbar.KeyboardShortcutHelp
import javax.inject.Inject

/** The app's single activity. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var shortcutHelp: KeyboardShortcutHelp

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { HATheme { AppRoot() } }
    }

    /** Lists the dashboards' keyboard shortcuts in Android's list of shortcuts (Meta + /). */
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>?, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data?.addAll(shortcutHelp.groups())
    }
}
