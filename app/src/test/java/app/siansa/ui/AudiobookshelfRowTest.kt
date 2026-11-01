package app.siansa.ui

import app.siansa.data.AiProvider
import app.siansa.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** The cards' second lines on Settings › Servers and services (issue #665): what each is, and whether it's set up. */
class AudiobookshelfRowTest {
    @Test fun audiobookshelfSaysWhetherItIsSetUp() {
        assertEquals("Audiobooks and podcasts · set up", absLine(ready = true))
        assertEquals("Audiobooks and podcasts · not set up", absLine(ready = false))
    }

    @Test fun theAiCardSaysWhichServiceAndModel() {
        assertEquals("Titles and sound searches · not set up", aiLine(AppSettings()))
        assertEquals("OpenRouter · m", aiLine(AppSettings(aiKey = "k", aiModel = "m")))
        assertEquals("Titles and sound searches · off", aiLine(AppSettings(aiKey = "k", aiTitles = false)))
        assertEquals("Ollama · llama3.2", aiLine(AppSettings(aiProvider = AiProvider.OLLAMA, aiAddress = "192.168.1.10:11434", aiModel = "llama3.2")))
    }

    @Test fun switchingServiceTakesItsUsualModel() {
        assertEquals("gpt-4.1-mini", aiModelFor(AiProvider.OPENAI, "google/gemini-2.5-flash"))
        assertEquals("my-model", aiModelFor(AiProvider.OTHER, "my-model"))
    }
}
