package io.homeassistant.companion.android.dashboard.ui.moreinfo

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.compose.PlayerSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.data.CameraRepository
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.moreinfo.MoreInfoExtra
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Port of `more-info-camera`: the live stream (`ha-camera-stream`: HLS when the camera offers it, else its MJPEG
 * stream), then the snapshot download, saved where the user picks (the system's file picker, which every Android
 * version has without a permission) as `snapshot_<camera>.jpg`.
 */
@Composable
internal fun MoreInfoCamera(camera: MoreInfoExtra.Camera, state: EntityState) {
    val viewModel = hiltViewModel<CameraViewModel>(key = "camera-${state.entityId}")
    val token = state.attributes.string("access_token")
    LaunchedEffect(state.entityId, token) { viewModel.show(state.entityId, token) }
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JPEG)) { uri ->
        uri?.let(viewModel::downloadSnapshot)
    }
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        // The picture fills the width at its own ratio, as upstream's `img`; a video at 16:9 until it plays
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val shown = stream) {
                is CameraStream.Hls -> Box(Modifier.fillMaxWidth().aspectRatio(VIDEO_RATIO)) {
                    HlsPlayer(shown.url, viewModel)
                }
                is CameraStream.Frame -> {
                    val bitmap =
                        remember(shown.jpeg) {
                            BitmapFactory.decodeByteArray(shown.jpeg, 0, shown.jpeg.size)?.asImageBitmap()
                        }
                    bitmap?.let {
                        Image(
                            it,
                            contentDescription = state.attributes.string("friendly_name"),
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                CameraStream.None -> Unit
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            HAFilledButton(
                camera.download,
                { saveAs.launch("snapshot_${state.entityId.replace('.', '_')}.jpg") },
                enabled = camera.downloadEnabled && download !is SnapshotDownload.Saving,
            )
        }
        // Upstream tells only of a failure (its toast)
        if ((download as? SnapshotDownload.Done)?.failed == true) {
            Text(
                camera.downloadFailed,
                style = HATextStyle.BodyMedium,
                color = LocalHAColorScheme.current.colorOnDangerNormal,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun HlsPlayer(url: String, viewModel: CameraViewModel) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(
                HlsMediaSource.Factory(
                    OkHttpDataSource.Factory(viewModel.client),
                ).createMediaSource(MediaItem.fromUri(url)),
            )
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    PlayerSurface(player, Modifier.fillMaxWidth())
}

/** What a camera's details stream. */
internal sealed interface CameraStream {
    data object None : CameraStream

    /** The HLS playlist at [url]. */
    data class Hls(val url: String) : CameraStream

    /** The MJPEG stream's latest frame. */
    class Frame(val jpeg: ByteArray) : CameraStream
}

/** A snapshot download: under way, or done (or [failed]). */
internal sealed interface SnapshotDownload {
    data object Idle : SnapshotDownload

    data object Saving : SnapshotDownload

    data class Done(val failed: Boolean) : SnapshotDownload
}

/** A camera's stream while its details show, and its snapshot download. */
@HiltViewModel
internal class CameraViewModel @VisibleForTesting constructor(
    private val repository: CameraRepository,
    private val context: Context,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        repository: CameraRepository,
        @ApplicationContext context: Context,
    ) : this(repository, context, Dispatchers.IO)

    private val _stream = MutableStateFlow<CameraStream>(CameraStream.None)
    val stream: StateFlow<CameraStream> = _stream.asStateFlow()
    private val _download = MutableStateFlow<SnapshotDownload>(SnapshotDownload.Idle)
    val download: StateFlow<SnapshotDownload> = _download.asStateFlow()

    /** The app's HTTP client, for the player; set once the stream starts. */
    lateinit var client: OkHttpClient
        private set

    private var entityId: String? = null
    private var token: String? = null

    /** Stream [camera], whose MJPEG stream and snapshots take [accessToken]. */
    fun show(camera: String, accessToken: String?) {
        token = accessToken
        if (entityId == camera) return
        entityId = camera
        viewModelScope.launch {
            client = repository.streamClient()
            val types = (repository.streamTypes(camera) as? Fetched.Success)?.value.orEmpty()
            // A stream that can't start shows the MJPEG stream, as upstream's does when HLS has no video
            val hls = if (HLS in types) {
                when (val url = repository.hlsUrl(camera)) {
                    is Fetched.Success -> url.value
                    is Fetched.Failure -> null.also {
                        Timber.w("Couldn't start the HLS stream of $camera: ${url.error}")
                    }
                }
            } else {
                null
            }
            if (hls != null) {
                _stream.value = CameraStream.Hls(hls)
                return@launch
            }
            // UPSTREAM-DIFF: a camera offering only WebRTC shows its MJPEG stream; upstream plays WebRTC
            val path = accessToken?.let { "/api/camera_proxy_stream/$camera?token=$it" } ?: return@launch
            repository.mjpegFrames(path)
                .catch { e -> Timber.w(e, "The MJPEG stream of $camera ended") }
                .collect { _stream.value = CameraStream.Frame(it) }
        }
    }

    /** Save the camera's picture to [target], a document the user picked. */
    fun downloadSnapshot(target: Uri) {
        val camera = entityId ?: return
        val accessToken = token ?: return
        _download.value = SnapshotDownload.Saving
        viewModelScope.launch {
            val saved = when (val snapshot = repository.snapshot(camera, accessToken)) {
                is Fetched.Success -> write(target, snapshot.value.first)
                is Fetched.Failure -> false.also {
                    Timber.w("Couldn't download the snapshot of $camera: ${snapshot.error}")
                }
            }
            _download.value = if (saved) SnapshotDownload.Done(failed = false) else SnapshotDownload.Done(failed = true)
        }
    }

    private suspend fun write(target: Uri, bytes: ByteArray): Boolean = withContext(io) {
        try {
            context.contentResolver.openOutputStream(target)?.use { it.write(bytes) } != null
        } catch (e: IOException) {
            Timber.w(e, "Couldn't save the snapshot to $target")
            false
        }
    }

    private companion object {
        const val HLS = "hls"
    }
}

private const val VIDEO_RATIO = 16f / 9f
private const val JPEG = "image/jpeg"
