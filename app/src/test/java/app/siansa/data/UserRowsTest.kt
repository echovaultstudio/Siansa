package app.siansa.data

import app.siansa.net.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** A user's row (#571): the user on the first line, the kind of server under it. */
class UserRowsTest {
    @Test fun theFirstLineIsTheUser() {
        assertEquals("max", UserFiles.title("max"))
        assertEquals("New user", UserFiles.title(""))
    }

    @Test fun theSecondLineIsTheKindOfServer() {
        assertEquals("OpenSubsonic", UserFiles.serverLine(SourceKind.OPENSUBSONIC, null, connected = true))
        assertEquals("Emby", UserFiles.serverLine(SourceKind.EMBY, " ", connected = true))
    }

    @Test fun aNamedServerAddsItsName() {
        assertEquals("Emby · Living room", UserFiles.serverLine(SourceKind.EMBY, "Living room", connected = true))
    }

    @Test fun notSetUpSaysSo() {
        assertEquals("Not connected yet", UserFiles.serverLine(SourceKind.OPENSUBSONIC, null, connected = false))
    }

    @Test fun theEntryReadsTheSame() {
        val e = UserEntry(1, "max on 192.168.1.106", connected = true, user = "max", kind = SourceKind.OPENSUBSONIC)
        assertEquals("max", e.title)
        assertEquals("OpenSubsonic", e.serverLine)
    }
}
