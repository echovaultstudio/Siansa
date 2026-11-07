package app.siansa.playback

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Siansa Dev's old "How it plays" setting, still saved on the owner's phone, is ignored (issue #114). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CastSettingsTest {
    @Test fun anOldSavedHowItPlaysIsIgnoredAndTheQualityStays() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("cast", Context.MODE_PRIVATE).edit()
            .putString("mode", "gapless").putString("quality", "opus").commit()
        // Read afresh, as on a new app start.
        CastSettings::class.java.getDeclaredField("prefs").apply { isAccessible = true }.set(CastSettings, null)
        CastSettings.load(context)
        assertEquals(CastQuality.Opus, CastSettings.quality.value)
        CastSettings.setQuality(CastQuality.Lossless)
    }
}
