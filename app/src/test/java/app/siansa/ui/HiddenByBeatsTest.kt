package app.siansa.ui

import app.siansa.data.AppSettings
import app.siansa.data.Service
import app.siansa.net.BeatsSound
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which services Settings › Services leaves out while Siansa Beats does their jobs (the owner, 17 and 18 Nov). */
class HiddenByBeatsTest {
    @After fun forget() = BeatsSound.restorePlans(false)

    private fun hidden(s: AppSettings) = Service.entries.filter { hiddenByBeats(s, it) }

    @Test fun audioMuseAndThePhonesAiGoWhileBeatsDoesTheirJobs() {
        val beats = AppSettings(beatsUrl = "http://beats.local:8090")
        assertEquals(listOf(Service.AUDIOMUSE), hidden(beats))  // no AI on Siansa Beats: the phone's AI stays
        BeatsSound.restorePlans(true)
        assertEquals(listOf(Service.AUDIOMUSE, Service.AI), hidden(beats))
        // Siansa Beats switched off: both come back.
        assertEquals(emptyList<Service>(), hidden(beats.copy(servicesOff = setOf(Service.BEATS.id))))
        // AudioMuse-AI and no Siansa Beats: nothing is left out, the phone's AI plans.
        assertEquals(emptyList<Service>(), hidden(AppSettings(audioMuseUrl = "http://am.local:8000", audioMuseToken = "t")))
    }
}
