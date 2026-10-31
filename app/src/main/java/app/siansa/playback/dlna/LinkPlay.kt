package app.siansa.playback.dlna

/**
 * LinkPlay's queue (issue #691): speakers built on LinkPlay's streaming module (JBL Bar, WiiM, Arylic, Audio Pro…)
 * offer a UPnP service of their own, `urn:schemas-wiimu-com:service:PlayQueue:1`, that takes a list of songs and
 * plays it through by itself. Siansa hands it the song playing and the next few, keeps that list in step with the
 * queue, and the speaker moves on without waiting for the app, so there's no gap while the app notices a song ended.
 *
 * The service isn't documented. The list format follows what LinkPlay speakers send back from BrowseQueue (as read by
 * other players, e.g. wiimplay): a `PlayList` with `ListName`, `ListInfo` and `Tracks` holding `Track1`, `Track2`…,
 * each with its `URL` and DIDL `Metadata`. Songs in it are numbered from 1.
 *
 * Plain text in and out, so it's tested without a speaker.
 */
internal object LinkPlay {
    const val SERVICE = "urn:schemas-wiimu-com:service:PlayQueue:1"

    /** The name of Siansa's list on the speaker. */
    const val QUEUE = "Siansa"

    /** The source the speaker shows for it. */
    const val SOURCE = "UPnPServer"

    /** How many songs after the one playing the speaker is given ahead. */
    const val AHEAD = 8

    /** One song for the speaker: Siansa's id, its address and its DIDL description. */
    data class Track(val id: String, val url: String, val metadata: String)

    /**
     * The list for CreateQueue and AppendTracksInQueue: [tracks] under [name], with [playIndex] (from 1) as the song
     * to start from. Fades are off: the speaker's own fade between songs would put back the gap.
     */
    fun queueContext(tracks: List<Track>, playIndex: Int = 1, name: String = QUEUE): String = buildString {
        append("<?xml version=\"1.0\"?>")
        append("<PlayList>")
        append("<ListName>").append(Upnp.escape(name)).append("</ListName>")
        append("<ListInfo>")
        append("<SourceName>").append(SOURCE).append("</SourceName>")
        append("<SearchUrl></SearchUrl>")
        append("<MarkSearch>0</MarkSearch>")
        append("<TrackNumber>").append(tracks.size).append("</TrackNumber>")
        append("<Quality>0</Quality>")
        append("<UpdateTime>0</UpdateTime>")
        append("<LastPlayIndex>").append(playIndex.coerceAtLeast(1)).append("</LastPlayIndex>")
        append("<RealIndex>0</RealIndex>")
        append("<SwitchPageMode>0</SwitchPageMode>")
        append("<CurrentPage>0</CurrentPage>")
        append("<TotalPages>0</TotalPages>")
        append("<FadeEnable>0</FadeEnable>")
        append("<FadeInMS>0</FadeInMS>")
        append("<FadeOutMS>0</FadeOutMS>")
        append("</ListInfo>")
        append("<Tracks>")
        tracks.forEachIndexed { i, t ->
            val tag = "Track${i + 1}"
            append("<").append(tag).append(">")
            append("<URL>").append(Upnp.escape(t.url)).append("</URL>")
            append("<Metadata>").append(Upnp.escape(t.metadata)).append("</Metadata>")
            append("<Id>").append(Upnp.escape(t.id)).append("</Id>")
            append("<Source>").append(SOURCE).append("</Source>")
            append("</").append(tag).append(">")
        }
        append("</Tracks>")
        append("</PlayList>")
    }

    /**
     * What to change on the speaker so the songs after the one playing are [wanted], when it has [have] after it
     * now (both Siansa song ids, in order).
     */
    data class Plan(
        /** Remove the speaker's songs from this place after the playing one (0 = the first after it); null keeps them. */
        val removeFrom: Int?,
        /** Then add these at the end. */
        val append: List<String>,
    ) {
        val nothing: Boolean get() = removeFrom == null && append.isEmpty()
    }

    /**
     * The smallest change: keep what already matches from the start, drop the rest, add what's missing. Most often
     * the queue only grew, or the speaker played one song, and only songs are added.
     */
    fun plan(have: List<String>, wanted: List<String>): Plan {
        var same = 0
        while (same < have.size && same < wanted.size && have[same] == wanted[same]) same++
        return Plan(removeFrom = if (same < have.size) same else null, append = wanted.drop(same))
    }

    /**
     * Whether a stop reported [stoppedPolls] times in a row is the end of the song. Without a list of its own, the
     * first one is. A speaker on its list may stop briefly between songs: only when it's on its last song, or stays
     * stopped for [STOPPED_POLLS] answers, has it ended.
     */
    fun ended(listed: Boolean, at: Int, listSize: Int, stoppedPolls: Int): Boolean =
        !listed || at >= listSize - 1 || stoppedPolls >= STOPPED_POLLS

    const val STOPPED_POLLS = 4

    /**
     * The songs (by place in Siansa's queue) after [index] that the speaker should have ahead: up to [AHEAD], in
     * play order, wrapping around with repeat all. None with repeat one, which Siansa does itself.
     */
    fun ahead(index: Int, size: Int, repeat: Int, count: Int = AHEAD): List<Int> {
        if (repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) return emptyList()
        val out = ArrayList<Int>()
        var at = index
        while (out.size < count) {
            at = DlnaQueue.nextIndex(at, size, repeat)
            if (at < 0 || at == index) break
            out += at
        }
        return out
    }
}
