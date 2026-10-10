package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.MediaBrowserRepository
import io.homeassistant.companion.android.dashboard.moreinfo.MediaBrowseId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/** One opened page of the browser: loading, loaded, or why it couldn't be. */
internal sealed interface BrowsePage {
    val id: MediaBrowseId

    data class Loading(override val id: MediaBrowseId) : BrowsePage

    data class Loaded(override val id: MediaBrowseId, val result: JsonObject) : BrowsePage

    data class Failed(override val id: MediaBrowseId, val error: LoadError) : BrowsePage
}

/** What loading the server's thumbnails needs: the brands token and the credentials, each `null` when unavailable. */
internal data class ThumbnailAuth(val brandsToken: String?, val authorization: String?)

/** A player's media browser: the pages opened, newest last, and what its thumbnails need. */
@HiltViewModel
internal class MediaBrowserViewModel @Inject constructor(private val repository: MediaBrowserRepository) : ViewModel() {
    private var entityId: String? = null
    private val _pages = MutableStateFlow<List<BrowsePage>>(emptyList())
    private val _thumbnails = MutableStateFlow(ThumbnailAuth(null, null))

    /** The opened pages; the last one shows. */
    val pages: StateFlow<List<BrowsePage>> = _pages.asStateFlow()

    /** What the thumbnails need to load. */
    val thumbnails: StateFlow<ThumbnailAuth> = _thumbnails.asStateFlow()

    /** Starts browsing [player]'s media at its root, with credentials for [serverUrl]. */
    fun start(player: String, serverUrl: String?) {
        if (entityId == player) return
        entityId = player
        _pages.value = emptyList()
        open(MediaBrowseId(null, null))
        viewModelScope.launch {
            val token = when (val result = repository.brandsToken()) {
                is Fetched.Success -> result.value
                is Fetched.Failure -> null.also { Timber.w("Couldn't get the brands token: ${result.error}") }
            }
            _thumbnails.value = ThumbnailAuth(token, serverUrl?.let { repository.authorization(it) })
        }
    }

    /** Opens [id] on top of the pages shown. */
    fun open(id: MediaBrowseId) {
        val player = entityId ?: return
        _pages.update { it + BrowsePage.Loading(id) }
        viewModelScope.launch {
            val page = when (val result = repository.browse(player, id)) {
                is Fetched.Success -> BrowsePage.Loaded(id, result.value)
                is Fetched.Failure -> BrowsePage.Failed(id, result.error).also {
                    Timber.w("Couldn't browse $player at ${id.contentId}: ${result.error}")
                }
            }
            _pages.update { pages -> pages.map { if (it is BrowsePage.Loading && it.id == id) page else it } }
        }
    }

    /** Loads the shown page again, after it failed. */
    fun retry() {
        val last = _pages.value.lastOrNull() ?: return
        _pages.update { it.dropLast(1) }
        open(last.id)
    }

    /** Goes back a page; `false` at the root. */
    fun back(): Boolean {
        if (_pages.value.size <= 1) return false
        _pages.update { it.dropLast(1) }
        return true
    }
}
