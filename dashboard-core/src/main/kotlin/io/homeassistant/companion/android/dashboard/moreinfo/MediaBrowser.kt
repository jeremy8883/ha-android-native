package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of `ha-media-player-browse` (frontend@20260624.6 src/components/media-player/ha-media-player-browse.ts)
// with `MediaClassBrowserSettings` and `mediaPlayerPlayMedia` (src/data/media-player.ts) and
// `ha-media-browser-thumbnail`'s URLs. Not ported: text-to-speech, manual entry, uploading and managing media.

/** Where to browse: [contentId] of [contentType], or the player's root when both are `null`. */
data class MediaBrowseId(val contentId: String?, val contentType: String?)

/**
 * One page of a player's media: its [title], a play button when it plays as a whole, and its children as a grid
 * (portrait for videos and shows) or a list.
 *
 * @property hiddenText how many children the player can't play were hidden, when some were
 */
data class MediaBrowsePage(
    val title: String,
    val play: CardAction.CallService?,
    val thumbnail: String?,
    val layout: MediaBrowseLayout,
    val children: List<MediaBrowseChild>,
    val emptyText: String?,
    val hiddenText: String?,
    val playLabel: String,
)

/** How a page shows its children. */
sealed interface MediaBrowseLayout {
    /** Cards, [portrait] for posters. */
    data class Grid(val portrait: Boolean) : MediaBrowseLayout

    /** Rows, with their thumbnails as [images] (else their class's icon). */
    data class List(val images: Boolean) : MediaBrowseLayout
}

/**
 * A child: a folder to open ([open]) and/or media to [play], with its thumbnail or its class's [icon].
 *
 * @property centered whether its thumbnail is a logo to centre (apps and folders) rather than fill
 */
data class MediaBrowseChild(
    val title: String,
    val icon: String,
    val thumbnail: String?,
    val centered: Boolean,
    val open: MediaBrowseId?,
    val play: CardAction.CallService?,
)

/** Where a thumbnail comes from. */
sealed interface ThumbnailSource {
    /** An integration's logo from the server's brands API, signed with its token. */
    data class Brand(val domain: String) : ThumbnailSource

    /** A path on the server, fetched with the user's credentials. */
    data class Local(val path: String) : ThumbnailSource

    /** Anywhere else, as it is. */
    data class Remote(val url: String) : ThumbnailSource
}

/** Port of `ha-media-browser-thumbnail`'s `resolveThumbnailURL`: where [url] is loaded from. */
fun thumbnailSource(url: String): ThumbnailSource = when {
    url.startsWith(BRANDS_PATH) -> ThumbnailSource.Brand(url.removePrefix(BRANDS_PATH).substringBefore('/'))
    url.startsWith(
        BRANDS_CDN,
    ) -> ThumbnailSource.Brand(url.removePrefix(BRANDS_CDN).substringAfter("_/").substringBefore('/'))
    url.startsWith("/") -> ThumbnailSource.Local(url)
    else -> ThumbnailSource.Remote(url)
}

/** The page [result] (`media_player/browse_media`) shows for [entityId]. */
fun HassSnapshot.mediaBrowsePage(entityId: String, result: JsonObject): MediaBrowsePage {
    val children = (result["children"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val childrenClass =
        result.string("children_media_class")?.let(CLASS_SETTINGS::get) ?: CLASS_SETTINGS.getValue(DIRECTORY)
    val pageClass = result.string("media_class")?.let(CLASS_SETTINGS::get)
    val hidden = (result["not_shown"] as? JsonPrimitive)?.content?.toIntOrNull()?.takeIf { it > 0 }
    return MediaBrowsePage(
        title = result.string("title").orEmpty(),
        play = playCall(entityId, result),
        thumbnail = result.string("thumbnail")?.ifEmpty { null },
        layout = if (childrenClass.grid) {
            MediaBrowseLayout.Grid(childrenClass.portrait)
        } else {
            MediaBrowseLayout.List(pageClass?.listImages == true)
        },
        children = children.map { child(entityId, it) },
        emptyText = localize("ui.components.media-browser.no_items").takeIf { children.isEmpty() },
        hiddenText = hidden?.let { localize("ui.components.media-browser.not_shown", mapOf("count" to "$it")) },
        playLabel = localize("ui.components.media-browser.play"),
    )
}

private fun child(entityId: String, item: JsonObject): MediaBrowseChild {
    val mediaClass = item.string("media_class")
    // A folder shows what it holds
    val iconClass = if (mediaClass == DIRECTORY) item.string("children_media_class") ?: mediaClass else mediaClass
    return MediaBrowseChild(
        title = item.string("title").orEmpty(),
        icon = iconClass?.let(CLASS_SETTINGS::get)?.icon ?: DEFAULT_ICON,
        thumbnail = item.string("thumbnail")?.ifEmpty { null },
        centered = mediaClass == "app" || mediaClass == DIRECTORY,
        open = MediaBrowseId(item.string("media_content_id"), item.string("media_content_type"))
            .takeIf { item.boolean("can_expand") == true },
        play = playCall(entityId, item),
    )
}

/** Port of `mediaPlayerPlayMedia`: text-to-speech is announced. */
private fun playCall(entityId: String, item: JsonObject): CardAction.CallService? {
    val id = item.string("media_content_id")?.takeIf { item.boolean("can_play") == true } ?: return null
    val data = mapOf(
        "entity_id" to JsonPrimitive(entityId),
        "media_content_id" to JsonPrimitive(id),
        "media_content_type" to JsonPrimitive(item.string("media_content_type").orEmpty()),
    ) + listOfNotNull(("announce" to JsonPrimitive(true)).takeIf { id.startsWith(TTS_PREFIX) })
    return CardAction.CallService("media_player", "play_media", JsonObject(data), target = null)
}

/** `MediaClassBrowserSettings`: a class's icon, whether it shows as a grid (portrait), and list thumbnails. */
private class ClassSettings(
    val icon: String,
    val grid: Boolean = false,
    val portrait: Boolean = false,
    val listImages: Boolean = false,
)

private const val DIRECTORY = "directory"
private const val DEFAULT_ICON = "mdi:folder"
private const val TTS_PREFIX = "media-source://tts/"
private const val BRANDS_PATH = "/api/brands/integration/"
private const val BRANDS_CDN = "https://brands.home-assistant.io/"

private val CLASS_SETTINGS = mapOf(
    "album" to ClassSettings("mdi:album", grid = true),
    "app" to ClassSettings("mdi:application", grid = true, listImages = true),
    "artist" to ClassSettings("mdi:account-music", grid = true, listImages = true),
    "channel" to ClassSettings("mdi:television-classic", grid = true, portrait = true, listImages = true),
    "composer" to ClassSettings("mdi:account-music-outline", grid = true, listImages = true),
    "contributing_artist" to ClassSettings("mdi:account-music", grid = true, listImages = true),
    DIRECTORY to ClassSettings(DEFAULT_ICON, grid = true, listImages = true),
    "episode" to ClassSettings("mdi:television-classic", grid = true, portrait = true, listImages = true),
    "game" to ClassSettings("mdi:gamepad-variant", grid = true, portrait = true),
    "genre" to ClassSettings("mdi:drama-masks", grid = true, listImages = true),
    "image" to ClassSettings("mdi:image", grid = true, listImages = true),
    "movie" to ClassSettings("mdi:movie", grid = true, portrait = true, listImages = true),
    "music" to ClassSettings("mdi:music", listImages = true),
    "playlist" to ClassSettings("mdi:playlist-music", grid = true, listImages = true),
    "podcast" to ClassSettings("mdi:podcast", grid = true),
    "season" to ClassSettings("mdi:television-classic", grid = true, portrait = true, listImages = true),
    "track" to ClassSettings("mdi:file-music"),
    "tv_show" to ClassSettings("mdi:television-classic", grid = true, portrait = true),
    "url" to ClassSettings("mdi:web"),
    "video" to ClassSettings("mdi:video", grid = true, listImages = true),
)
