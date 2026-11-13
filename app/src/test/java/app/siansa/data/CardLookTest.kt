package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How Home's cards' colours are kept (the owner, 13 Nov). */
class CardLookTest {
    @Test fun sectionsOwnLooksAreKeptAndReadBack() {
        val looks = mapOf(HomeSection.TODAY to CardLook.SOFT, HomeSection.RADIOS to CardLook.PLAIN)
        assertEquals("today:soft,radios:plain", CardLook.format(looks))
        assertEquals(looks, CardLook.parse("today:soft,radios:plain"))
    }

    @Test fun unknownAndAsHomeAreDropped() {
        assertEquals(mapOf(HomeSection.BOOKS to CardLook.ARTWORK), CardLook.parse("books:artwork,nope:soft,today:as_home,radios:loud,broken"))
        assertEquals(emptyMap<HomeSection, CardLook>(), CardLook.parse(null))
        assertEquals(emptyMap<HomeSection, CardLook>(), CardLook.parse(""))
    }

    @Test fun theChoicesForHomeLeaveOutAsHome() {
        assertEquals(listOf(CardLook.VIVID, CardLook.SOFT, CardLook.PLAIN, CardLook.ARTWORK), CardLook.forHome)
        assertEquals(CardLook.VIVID, CardLook.DEFAULT)
        assertEquals(CardLook.SOFT, CardLook.of("soft"))
        assertNull(CardLook.of("edge"))
    }
}
