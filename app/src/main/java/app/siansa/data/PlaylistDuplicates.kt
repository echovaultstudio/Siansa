package app.siansa.data

/**
 * Songs already in a playlist (issue #1235; the owner, 18 Nov: "a number red circled 1 or how many times that track
 * exist in the playlist beside the playlist name"). Navidrome, Emby and Jellyfin all list a playlist's entries by the
 * song's own id, the id the song is added by, so one count works on all three; a phone playlist keeps the songs.
 */
object PlaylistDuplicates {
    /** How many entries of the playlist ([inPlaylist]: its songs' ids, in order) are one of [adding]: for one song, how many times it's there. */
    fun count(inPlaylist: List<String>, adding: List<Song>): Int {
        val wanted = adding.mapTo(HashSet()) { it.id }
        return inPlaylist.count { it in wanted }
    }

    /** Those of [adding] the playlist doesn't have yet, in their order: "Add only the new ones". */
    fun missing(inPlaylist: List<String>, adding: List<Song>): List<Song> {
        val there = inPlaylist.toHashSet()
        return adding.filter { it.id !in there }
    }

    /** What the question says before adding: "This song is in it twice already." or "3 of 12 songs are in it already." */
    fun question(inPlaylist: List<String>, adding: List<Song>): String {
        val times = count(inPlaylist, adding)
        if (adding.size == 1) return "This song is in it ${timesWord(times)} already."
        val there = adding.size - missing(inPlaylist, adding).size
        return if (there == adding.size) "All ${adding.size} songs are in it already." else "$there of ${adding.size} songs are in it already."
    }

    private fun timesWord(n: Int) = when (n) { 1 -> "once"; 2 -> "twice"; else -> "$n times" }
}
