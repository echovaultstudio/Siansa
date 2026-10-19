package app.musix.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Concepts set to "more" are sent as words too, so they help choose the songs (issue #232). */
class SoundQueryTest {
    @Test fun moreConceptsJoinTheWords() {
        val chips = linkedMapOf("female vocals" to true, "sad" to true, "acoustic" to true)
        assertEquals("sad songs, female vocals, acoustic", soundQuery("sad songs", chips))
    }

    @Test fun lessConceptsStayOutOfTheWords() {
        assertEquals("rainy jazz, piano", soundQuery(" rainy jazz ", linkedMapOf("electronic" to false, "piano" to true)))
    }

    @Test fun wordsAlreadyTypedAreNotRepeated() {
        assertEquals("Sad acoustic female voice", soundQuery("Sad acoustic female voice", mapOf("acoustic" to true, "Sad" to true)))
        // "sad" is not in "saddle", so it is added.
        assertEquals("saddle song, sad", soundQuery("saddle song", mapOf("sad" to true)))
        assertEquals("calm", soundQuery("calm", emptyMap()))
    }
}
