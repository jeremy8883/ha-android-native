package io.homeassistant.companion.android.dashboard.data

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.dashboard.model.string
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/** A camera's streams and snapshots on the active server, as `ha-camera-stream` and the details' download use them. */
class CameraRepository @VisibleForTesting internal constructor(
    private val serverManager: ServerManager,
    private val okHttpClientProvider: SuspendProvider<OkHttpClient>,
    private val io: CoroutineDispatcher,
) {
    @Inject
    constructor(serverManager: ServerManager, okHttpClientProvider: SuspendProvider<OkHttpClient>) :
        this(serverManager, okHttpClientProvider, Dispatchers.IO)

    /** The stream types the frontend can play for [entityId] (`camera/capabilities`: "hls", "web_rtc"). */
    suspend fun streamTypes(entityId: String): Fetched<List<String>> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        return webSocket.request(
            "camera/capabilities",
            mapOf("entity_id" to entityId),
        ).expect<JsonObject>().map { result ->
            (result["frontend_stream_types"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
        }
    }

    /** The HLS playlist of [entityId] (`camera/stream`), as a full URL. */
    suspend fun hlsUrl(entityId: String): Fetched<String> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        return webSocket.request(
            "camera/stream",
            mapOf("entity_id" to entityId, "format" to "hls"),
        ).expect<JsonObject>()
            .flatMap { result ->
                val path =
                    result.string("url")
                        ?: return@flatMap Fetched.Failure(LoadError.UnexpectedResponse("camera/stream"))
                url(path)?.let { Fetched.Success(it) } ?: Fetched.Failure(LoadError.NoServer)
            }
    }

    /**
     * The frames of the MJPEG stream at [path] (`/api/camera_proxy_stream/...`), each a JPEG, as the browser shows
     * a `multipart/x-mixed-replace` image; ends when the server closes it, fails with the reason.
     */
    fun mjpegFrames(path: String): Flow<ByteArray> = flow {
        val url = url(path) ?: throw IOException("No server")
        val client = streamClient()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val input = response.body?.byteStream() ?: throw IOException("No body")
            while (true) {
                currentCoroutineContext().ensureActive()
                val frame = input.nextJpeg() ?: break
                emit(frame)
            }
        }
    }.flowOn(io)

    /**
     * The app's HTTP client for endless responses (an MJPEG stream, HLS segments): without its application
     * interceptors, which in debug builds log whole bodies, and without a read timeout.
     */
    suspend fun streamClient(): OkHttpClient = okHttpClientProvider().newBuilder()
        .apply { interceptors().clear() }
        .readTimeout(Duration.ZERO)
        .build()

    /** The camera's current picture (`/api/camera_proxy/...` with its [token]) and its content type. */
    suspend fun snapshot(entityId: String, token: String): Fetched<Pair<ByteArray, String?>> = withContext(io) {
        val url =
            url("/api/camera_proxy/$entityId?token=$token")
                ?: return@withContext Fetched.Failure(LoadError.NoServer)
        try {
            okHttpClientProvider().newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Fetched.Failure(
                        LoadError.Server(code = "${response.code}", message = response.message, translation = null),
                    )
                } else {
                    response.body?.bytes()?.let { Fetched.Success(it to response.header("Content-Type")) }
                        ?: Fetched.Failure(LoadError.NoResponse)
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Couldn't download the snapshot of $entityId")
            Fetched.Failure(LoadError.NoResponse)
        }
    }

    private suspend fun url(path: String): String? {
        if (path.startsWith("http")) return path
        val base = (serverManager.connectionStateProvider().urlFlow().first() as? UrlState.HasUrl)?.url?.toString()
        return base?.removeSuffix("/")?.plus(path)
    }
}

/** The next JPEG of a multipart stream: from its start marker to its end marker, `null` at the end. */
private fun InputStream.nextJpeg(): ByteArray? {
    if (!skipTo(START)) return null
    val frame = ByteArrayOutputStream().apply {
        write(MARKER)
        write(START)
    }
    return frame.takeIf { copyTo(END, it) }?.toByteArray()
}

/** Read up to the marker ending with [code]; `false` at the end of the stream. */
private fun InputStream.skipTo(code: Int): Boolean = copyTo(code, null)

/** Read up to the marker ending with [code], into [out] when given; `false` at the end of the stream. */
private fun InputStream.copyTo(code: Int, out: ByteArrayOutputStream?): Boolean {
    var previous = -1
    var current = read()
    while (current >= 0 && !(previous == MARKER && current == code)) {
        out?.write(current)
        previous = current
        current = read()
    }
    if (current >= 0) out?.write(current)
    return current >= 0
}

private const val MARKER = 0xFF
private const val START = 0xD8
private const val END = 0xD9
