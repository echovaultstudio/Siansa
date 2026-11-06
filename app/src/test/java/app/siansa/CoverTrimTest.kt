package app.siansa

import android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
import android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
import android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Issue #892: the TV said "critical" every minute and lost every cover on screen each time. */
class CoverTrimTest {
    private val max = 30_000_000L

    @Test fun onScreenHalfTheCoversStayEvenWhenMemoryIsCritical() {
        assertEquals(max / 2, CoverTrim.keep(TRIM_MEMORY_RUNNING_CRITICAL, max))
        assertEquals(max / 2, CoverTrim.keep(TRIM_MEMORY_RUNNING_LOW, max))
        assertNull(CoverTrim.keep(TRIM_MEMORY_RUNNING_MODERATE, max))
    }

    @Test fun outOfSightTheyGo() {
        assertEquals(max / 2, CoverTrim.keep(TRIM_MEMORY_UI_HIDDEN, max))
        assertEquals(0L, CoverTrim.keep(TRIM_MEMORY_BACKGROUND, max))
        assertEquals(0L, CoverTrim.keep(TRIM_MEMORY_COMPLETE, max))
    }
}
