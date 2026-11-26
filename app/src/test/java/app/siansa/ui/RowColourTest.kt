package app.siansa.ui

import app.siansa.data.CardLook
import app.siansa.data.PictureColours
import org.junit.Assert.assertEquals
import org.junit.Test

/** A row's one Colour (the owner, 25 Nov: "There is x2 colour selectors and its impossible to know what does what"). */
class RowColourTest {
    @Test fun asHomeVividYourListeningsSixThenTheSets() {
        assertEquals(
            listOf("as_home", "vivid", "teal", "plum", "ink", "ember", "moss", "cover", "mood", "siansa", "mono"),
            RowColour.all.map { it.id },
        )
        // A card colour comes with pictures from the covers; a set with no card colour of its own.
        RowColour.all.forEach { assertEquals(it, RowColour.of(it.look, it.set)) }
    }

    @Test fun whatARowHadBecomesOneChoice() {
        assertEquals(RowColour(CardLook.AS_HOME, PictureColours.MOOD), RowColour.of(CardLook.AS_HOME, PictureColours.MOOD))
        // Both chosen before the two were one: its own card colour wins.
        assertEquals(RowColour(CardLook.PLUM, PictureColours.COVERS), RowColour.of(CardLook.PLUM, PictureColours.MONO))
        assertEquals(RowColour(CardLook.AS_HOME, PictureColours.COVERS), RowColour.of(CardLook.AS_HOME, PictureColours.COVERS))
    }

    @Test fun itsNameSaysWhatItIs() {
        assertEquals("As Home (Teal)", RowColour.all.first().label(CardLook.TEAL))
        assertEquals("As Home (Vivid)", RowColour.all.first().label(CardLook.VIVID))
        assertEquals("Vivid", RowColour.of(CardLook.VIVID, PictureColours.COVERS).label(CardLook.TEAL))
        assertEquals("Ember", RowColour.of(CardLook.EMBER, PictureColours.COVERS).label(CardLook.TEAL))
        assertEquals("By mood", RowColour.of(CardLook.AS_HOME, PictureColours.MOOD).label(CardLook.TEAL))
    }
}
