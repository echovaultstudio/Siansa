package app.siansa.mix

/**
 * How many of AudioMuse's test songs are in the synced library ([total] found, [byId] with the same id, [byName]
 * only by title and artist), and whether there is a library to compare with ([librarySongs]).
 */
data class LibraryMatch(val total: Int, val byId: Int, val byName: Int, val librarySongs: Int)

/**
 * What the AudioMuse test in Settings says (beta testers #32): not just that AudioMuse answers, but whether its
 * songs are this server's. AudioMuse that analysed another server answers fine and yet every vibe comes out
 * empty. Returns whether all is well, and the words to show.
 */
fun audioMuseCheck(match: LibraryMatch, otherServer: Boolean): Pair<Boolean, String> = when {
    match.total == 0 -> false to "Connected, but AudioMuse found no songs. Has it analysed your music yet?"
    otherServer || match.librarySongs == 0 ->
        true to "Connected: AudioMuse found ${match.total} songs. Save and sync to check they're in your library."
    match.byId > 0 -> true to "Connected: AudioMuse's songs are in your library."
    match.byName > 0 -> true to
        "Connected. AudioMuse knows this music from another server, so songs are matched by title and artist " +
        "(${match.byName} of ${match.total} found here). If AudioMuse also reads this server, enter its name below."
    else -> false to
        "Connected, but none of AudioMuse's songs are in your library here. AudioMuse may read another server: " +
        "enter this server's name in AudioMuse below, or have AudioMuse analyse it."
}
