package app.siansa.ui

import app.siansa.data.AppSettings
import app.siansa.playback.CrossfadeMode
import app.siansa.playback.LoudnessSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeLengthHintTest {
    private val smart = AppSettings(crossfade = CrossfadeMode.SMART, loudnessSource = LoudnessSource.PLUGIN)

    @Test fun withSmartTheLengthIsTheLongestAFadeCanBe() {
        val hint = crossfadeLengthHint(smart)!!
        assertTrue(hint, hint.startsWith("The longest a fade can be."))
        // DJ blends are always on in DJ playback (no setting any more), so it always says when the full length is used.
        assertTrue(hint, hint.endsWith("songs not measured yet and for a quiet intro without a beat in DJ blends."))
    }

    @Test fun otherwiseTheModesOwnHintSaysIt() {
        assertNull(crossfadeLengthHint(smart.copy(crossfade = CrossfadeMode.FIXED)))
        assertNull(crossfadeLengthHint(smart.copy(crossfade = CrossfadeMode.OFF)))
        // Without the plugin, Smart's own hint already says the length is used as it is.
        assertNull(crossfadeLengthHint(smart.copy(loudnessSource = LoudnessSource.TAGS)))
        assertEquals(null, crossfadeLengthHint(AppSettings()))
    }
}
