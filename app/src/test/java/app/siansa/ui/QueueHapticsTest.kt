package app.siansa.ui

import android.os.VibrationEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Swipe to queue's vibrations: a click as the strip shows, a heavier one when the song is added or removed. */
class QueueHapticsTest {
    @Test fun predefinedClicksOnAndroid10AndLater() {
        assertEquals(QueueHaptics.Effect(VibrationEffect.EFFECT_CLICK, 0), QueueHaptics.effect(QueueHaptics.Kind.START, 29))
        assertEquals(QueueHaptics.Effect(VibrationEffect.EFFECT_HEAVY_CLICK, 0), QueueHaptics.effect(QueueHaptics.Kind.ADDED, 34))
    }

    @Test fun removingFeelsLikeAdding() {
        // The swipe out of the queue is the swipe into it in reverse: the same firm buzz.
        for (sdk in listOf(26, 29, 34)) {
            assertEquals(QueueHaptics.effect(QueueHaptics.Kind.ADDED, sdk), QueueHaptics.effect(QueueHaptics.Kind.REMOVED, sdk))
        }
    }

    @Test fun shortPulsesBefore() {
        val start = QueueHaptics.effect(QueueHaptics.Kind.START, 28)
        val added = QueueHaptics.effect(QueueHaptics.Kind.ADDED, 26)
        assertEquals(null, start.predefined)
        assertTrue("Adding is felt more than starting", added.pulseMs > start.pulseMs)
    }
}
