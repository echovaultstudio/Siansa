package app.siansa.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether a newer Siansa is out, from the F-Droid repo's list (the owner, 14 Nov). */
class UpdateCheckTest {
    private val index = """
        {"repo": {"name": "Siansa"}, "apps": [],
         "packages": {
           "app.siansa": [
             {"versionName": "1.4.2", "versionCode": 140200},
             {"versionName": "1.5.0", "versionCode": 150000},
             {"versionName": "1.5.0", "versionCode": 150001}
           ],
           "app.screenkeeper": [{"versionName": "9.0.0", "versionCode": 1}]
         }}
    """.trimIndent()

    @Test fun theHighestVersionOfThisAppIsFound() {
        assertEquals("1.5.0", UpdateCheck.latestIn(index, "app.siansa"))
        assertNull(UpdateCheck.latestIn(index, "app.other"))
        assertNull(UpdateCheck.latestIn("not json", "app.siansa"))
    }

    @Test fun onlyAHigherVersionIsAnUpdate() {
        assertTrue(UpdateCheck.isNewer("1.5.0", "1.4.2"))
        assertTrue(UpdateCheck.isNewer("1.10.0", "1.9.9"))
        assertFalse(UpdateCheck.isNewer("1.5.0", "1.5.0"))
        assertFalse(UpdateCheck.isNewer("1.4.2", "1.5.0"))
        assertFalse(UpdateCheck.isNewer("1.5.0", "1.5.0-dev"))
        assertFalse(UpdateCheck.isNewer("garbage", "1.5.0"))
    }
}
