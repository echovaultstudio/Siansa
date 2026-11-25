package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How Home's cards' colours are kept (the owner, 13 Nov; 25 Nov: Your listening's six colours). */
class CardLookTest {
    @Test fun sectionsOwnLooksAreKeptAndReadBack() {
        val looks = mapOf(HomeSection.TODAY to CardLook.PLUM, HomeSection.RADIOS to CardLook.COVER)
        assertEquals("today:plum,radios:cover", CardLook.format(looks))
        assertEquals(looks, CardLook.parse("today:plum,radios:cover"))
    }

    @Test fun unknownAndAsHomeAreDropped() {
        assertEquals(mapOf(HomeSection.BOOKS to CardLook.EMBER), CardLook.parse("books:ember,nope:teal,today:as_home,radios:loud,broken"))
        assertEquals(emptyMap<HomeSection, CardLook>(), CardLook.parse(null))
        assertEquals(emptyMap<HomeSection, CardLook>(), CardLook.parse(""))
    }

    /** The owner, 25 Nov: "the card colours choices for all possible cards should follow listening card choices, names and same design". */
    @Test fun theChoicesAreYourListeningsColours() {
        assertEquals(ListenColour.entries, CardLook.forHome.map { it.colour })
        assertEquals(ListenColour.entries.map { it.label }, CardLook.forHome.map { it.label })
        assertEquals(ListenColour.entries.map { it.id }, CardLook.forHome.map { it.id })
        assertEquals(CardLook.TEAL, CardLook.DEFAULT)
        assertEquals("As Home", CardLook.AS_HOME.label)
        assertNull(CardLook.of("edge"))
    }

    @Test fun lookChosenBeforeAreReadAsTheNearestColour() {
        assertEquals(CardLook.DEFAULT, CardLook.of("vivid"))
        assertEquals(CardLook.INK, CardLook.of("soft"))
        assertEquals(CardLook.INK, CardLook.of("plain"))
        assertEquals(CardLook.COVER, CardLook.of("artwork"))
        assertEquals(mapOf(HomeSection.TODAY to CardLook.INK, HomeSection.BOOKS to CardLook.COVER), CardLook.parse("today:soft,books:artwork"))
    }
}
