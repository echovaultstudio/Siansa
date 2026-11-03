package app.siansa.mix

import app.siansa.data.Song

/**
 * How an artist radio is put together when AudioMuse-AI is there: songs that sound like the artist (AudioMuse's
 * blend of their songs), with one of the artist's own songs every [ownEvery] songs, so it stays their radio and
 * doesn't turn into only their songs or only other people's.
 */
object ArtistRadioMix {
    /** One in this many songs is the artist's own. */
    const val OWN_EVERY = 4
    /** The artist's own songs AudioMuse blends into "their sound". */
    const val SEEDS = 12
    /** At most this many songs of any one other artist. */
    const val PER_ARTIST = 3

    /**
     * [size] songs from [alike] (sound-alikes, best first) and [own] (the artist's songs), leaving out [already]
     * (what's queued) and repeats; the artist's own songs come in every [ownEvery]. When there's too little of
     * one, the other fills in.
     */
    fun mix(own: List<Song>, alike: List<Song>, already: Set<String>, size: Int, ownEvery: Int = OWN_EVERY, artistOf: (Song) -> String = { it.artist }): List<Song> {
        val used = HashSet(already)
        val ownIds = own.map { it.id }.toSet()
        val perArtist = HashMap<String, Int>()
        val others = alike.filter { s ->
            if (s.id in ownIds || s.id in used) return@filter false
            val key = artistOf(s).lowercase()
            val n = perArtist[key] ?: 0
            if (n >= PER_ARTIST) return@filter false
            perArtist[key] = n + 1
            used.add(s.id)
        }.iterator()
        val mine = own.filter { used.add(it.id) }.iterator()
        val out = ArrayList<Song>()
        while (out.size < size && (others.hasNext() || mine.hasNext())) {
            val ownTurn = (out.size + 1) % ownEvery == 0
            out += when {
                ownTurn && mine.hasNext() -> mine.next()
                others.hasNext() -> others.next()
                else -> mine.next()
            }
        }
        return out
    }
}
