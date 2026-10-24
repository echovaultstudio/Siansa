package app.siansa.mix

import app.siansa.net.ClapHit
import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #416: AudioMuse analysed one server, the app is on another, so tracks are matched by name. */
class MatchByNameTest {
    private val index = SongIndex(
        listOf(
            SongIndex.Entry("880222", "Ignorance (acoustic version)", "Paramore"),
            SongIndex.Entry("880223", "Red Red Wine", "UB40"),
        ),
    )

    private fun hit(id: String, title: String, artist: String) = ClapHit(id, title, artist, null, 0.5)

    @Test fun matchesTracksWhoseIdsAreFromAnotherServer() {
        val found = matchByName(
            listOf(hit("7nSYlrsZal93VJceaej4Mt", "Ignorance (Acoustic Version)", "paramore"), hit("0u0sfrapaEGXK7aC2FQofF", "Red Red Wine", "UB40")),
            index,
        )
        assertEquals(mapOf("7nSYlrsZal93VJceaej4Mt" to "880222", "0u0sfrapaEGXK7aC2FQofF" to "880223"), found)
    }

    @Test fun leavesOutTracksTheLibraryDoesNotHave() {
        assertEquals(emptyMap<String, String>(), matchByName(listOf(hit("x", "Unknown song", "Nobody")), index))
    }
}
