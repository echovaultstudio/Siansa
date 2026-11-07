package app.siansa.data

import kotlinx.serialization.Serializable

/*
 * Your Stations (issue #353): internet radio and podcasts in one place under Your Library. Radio stations come from
 * the music server (Navidrome keeps a list) and from links you add; podcasts from RSS feeds you add. What you add is
 * kept on this phone only.
 */

/** A radio station: from the server ([fromServer]) or added on this phone. [link] is what was typed or sent. */
@Serializable
data class Station(
    val id: String,
    val name: String,
    /** The address that plays, once a playlist link (.m3u, .pls) has been looked into. */
    val streamUrl: String,
    val link: String = streamUrl,
    val homePage: String? = null,
    /** A picture's web address, when there is one: the [pictureLink], or one found by itself (StationPictures). */
    val image: String? = null,
    val fromServer: Boolean = false,
    val addedAt: Long = 0,
    /** A picture link entered when editing the station; null finds one by itself. */
    val pictureLink: String? = null,
    /** When its picture was looked for; 0 while it still needs looking for. */
    val pictureCheckedAt: Long = 0,
)

/**
 * The picture of one of the server's stations, kept on the phone by its stream address (the server has no place for
 * one): what was found ([image], a [homePage] the server didn't give) or the [pictureLink] entered for it.
 */
@Serializable
data class StationPicture(
    val image: String? = null,
    val homePage: String? = null,
    val pictureLink: String? = null,
    val checkedAt: Long = 0,
)

/** One podcast episode from a feed. [url] is its audio file (the RSS enclosure). */
@Serializable
data class Episode(
    val id: String,
    val title: String,
    val url: String,
    val publishedAt: Long = 0,
    val durationSec: Int = 0,
    val image: String? = null,
    val description: String? = null,
)

/** A podcast added by its RSS feed's address, with the episodes it had when last read. */
@Serializable
data class PodcastFeed(
    val id: String,
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val image: String? = null,
    val description: String? = null,
    val episodes: List<Episode> = emptyList(),
    val addedAt: Long = 0,
    val checkedAt: Long = 0,
)

/** Everything added on this phone, kept in one file per user. */
@Serializable
data class OwnStations(
    val stations: List<Station> = emptyList(),
    val podcasts: List<PodcastFeed> = emptyList(),
)

/**
 * Stations and episodes as songs, so the one player plays them (issue #353). Their ids say what they are and carry
 * their address: `live:<stream>` for a live station, `ep:<file>` for a podcast episode; covers are `web:<picture>`.
 * Nothing with these ids goes to the music server: no scrobbles, no history, no crossfades, no song cache.
 */
object OutsideMedia {
    const val LIVE = "live:"
    const val EPISODE = "ep:"
    const val COVER = "web:"

    /** Added to an outside address in the player, so the song cache leaves it alone; never sent (it's a fragment). */
    const val MARK = "siansa-outside"

    fun isLive(id: String?): Boolean = id != null && id.startsWith(LIVE)
    fun isEpisode(id: String?): Boolean = id != null && id.startsWith(EPISODE)

    /** A station or an episode: not from the music server. */
    fun isOutside(id: String?): Boolean = isLive(id) || isEpisode(id)

    /** Whether a cover id is a picture's web address. */
    fun isWebCover(id: String?): Boolean = id != null && id.startsWith(COVER)

    /** The address an outside id plays, or null for a server song. */
    fun addressOf(id: String): String? = when {
        isLive(id) -> id.removePrefix(LIVE)
        isEpisode(id) -> id.removePrefix(EPISODE)
        else -> null
    }

    /** The address the player is given: [address] with the mark, so the song cache can tell it apart. */
    fun forPlayer(address: String): String = address.substringBefore('#') + "#" + MARK

    /** Whether the player's address is an outside one (see [forPlayer]), also with a reload marker after it. */
    fun isMarked(url: String): Boolean = url.contains("#$MARK")

    /** [url] without the mark (and anything added after it). */
    fun unmarked(url: String): String = url.substringBefore("#$MARK")

    fun cover(image: String?): String? = image?.takeIf { it.isNotBlank() }?.let { COVER + it }

    /** A station as a song: its name as the title, "Live radio" under it. */
    fun liveSong(station: Station): Song = Song(
        id = LIVE + station.streamUrl,
        title = station.name,
        artist = LIVE_ARTIST,
        album = station.name,
        coverArt = cover(station.image),
    )

    /** An episode as a song, with its podcast as the artist and album. */
    fun episodeSong(feed: PodcastFeed, episode: Episode): Song = Song(
        id = EPISODE + episode.url,
        title = episode.title,
        artist = feed.title,
        album = feed.title,
        coverArt = cover(episode.image ?: feed.image),
        durationSec = episode.durationSec,
    )

    /**
     * What a live station shows while it plays: the stream's own title (ICY "StreamTitle", usually "Artist - Song")
     * with the station's name under it, or the station as it is when the stream sends none.
     */
    fun nowOnAir(song: Song, streamTitle: String?): Song {
        if (!isLive(song.id)) return song
        val title = streamTitle?.trim()?.takeIf { it.isNotEmpty() && it != "-" && !it.equals(song.album, ignoreCase = true) } ?: return song
        return song.copy(title = title, artist = song.album.ifBlank { song.title })
    }

    const val LIVE_ARTIST = "Live radio"
}
