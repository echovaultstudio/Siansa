package app.siansa.data

/**
 * Whose "Server name in AudioMuse" a request about songs [ids] uses (issue #708). AudioMuse reads each music server
 * login as a server of its own, so a shown user's songs (the kids' library on the same Navidrome) are only known on
 * that user's AudioMuse server.
 *
 * - Songs: the user most of them belong to ([ownerOf], null for the user in use's own), the user in use on a tie.
 * - No songs (a text search, an artist by name): the user in use, unless their own music is hidden
 *   ([ownHidden]) and exactly one other user is shown, whose music it then is.
 */
fun audioMuseUserFor(ids: List<String>, ownerOf: (String) -> Int?, active: Int, shown: Set<Int>, ownHidden: Boolean): Int {
    if (ids.isEmpty()) {
        val others = shown - active
        return if (ownHidden && others.size == 1) others.first() else active
    }
    val counts = ids.groupingBy { ownerOf(it) ?: active }.eachCount()
    val top = counts.maxOf { it.value }
    return if (counts[active] == top) active else counts.entries.first { it.value == top }.key
}
