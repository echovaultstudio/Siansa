package app.musix.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.musix.data.AppSettings
import app.musix.data.ReplayGainMode
import app.musix.data.Song
import app.musix.net.SubsonicApi
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.pow

/** Songs currently queued, by id, so the player and the screens share the full song details. */
object SongRegistry {
    private val songs = ConcurrentHashMap<String, Song>()
    fun register(list: List<Song>) = list.forEach { songs[it.id] = it }
    fun get(id: String): Song? = songs[id]
}

fun Song.toMediaItem(api: SubsonicApi): MediaItem {
    val uri = Uri.parse(api.streamUrl(id))
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setGenre(genre)
        .setArtworkUri(coverArt?.let { Uri.parse(api.coverUrl(it, 600)) })
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(metadata)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .build()
}

/**
 * ReplayGain as a player volume. Volume can only lower the level, so positive gains are capped at
 * full volume; the peak value keeps louder-than-0 dB results from clipping.
 */
fun replayGainVolume(song: Song?, settings: AppSettings): Float {
    if (song == null || settings.replayGain == ReplayGainMode.OFF) return 1f
    val album = settings.replayGain == ReplayGainMode.ALBUM
    val gain = if (album) song.albumGain ?: song.trackGain else song.trackGain ?: song.albumGain
    val peak = if (album) song.albumPeak ?: song.trackPeak else song.trackPeak ?: song.albumPeak
    var volume = 10f.pow(((gain ?: 0f) + settings.preampDb) / 20f)
    if (peak != null && peak > 0f) volume = min(volume, 1f / peak)
    return volume.coerceIn(0f, 1f)
}
