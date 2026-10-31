package app.siansa

import app.siansa.data.TopBarStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** The phone's top bar setting (issue #681). */
class TopBarStyleTest {
    @Test fun savedValuesReadBackAndAnythingElseIsSeeThrough() {
        TopBarStyle.entries.forEach { assertEquals(it, TopBarStyle.parse(it.name)) }
        assertEquals(TopBarStyle.CLEAR, TopBarStyle.parse(null))
        assertEquals(TopBarStyle.CLEAR, TopBarStyle.parse("PURPLE"))
    }

    @Test fun eachChoiceSaysWhatItDoes() {
        assertEquals(listOf("See-through", "Black", "Hidden"), TopBarStyle.entries.map { it.label })
        TopBarStyle.entries.forEach { assert(it.hint.isNotBlank()) }
    }
}
