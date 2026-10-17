package app.musix.ui

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Test

/** Swipe to queue's vibrations use what each Android version has. */
class QueueHapticsTest {
    @Test fun thresholdTick() {
        assertEquals(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, QueueHaptics.threshold(34))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, QueueHaptics.threshold(33))
    }

    @Test fun addedBuzz() {
        assertEquals(HapticFeedbackConstants.CONFIRM, QueueHaptics.added(30))
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, QueueHaptics.added(29))
    }
}
