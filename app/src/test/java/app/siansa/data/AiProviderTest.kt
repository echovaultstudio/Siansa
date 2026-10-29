package app.siansa.data

import app.siansa.net.ApiException
import app.siansa.net.ChatApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The AI services for titles and sound searches (issue #665): addresses, when one is ready, and their answers. */
class AiProviderTest {
    @Test fun eachServiceHasItsAddress() {
        assertEquals("https://openrouter.ai/api/v1/chat/completions", aiCompletionsUrl(AiProvider.OPENROUTER, ""))
        assertEquals("https://api.openai.com/v1/chat/completions", aiCompletionsUrl(AiProvider.OPENAI, "ignored"))
        assertEquals("https://api.anthropic.com/v1/chat/completions", aiCompletionsUrl(AiProvider.ANTHROPIC, ""))
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", aiCompletionsUrl(AiProvider.GEMINI, ""))
    }

    @Test fun oneOnYourNetworkTakesTheTypedAddress() {
        assertEquals("http://192.168.1.10:11434/v1/chat/completions", aiCompletionsUrl(AiProvider.OLLAMA, "192.168.1.10:11434"))
        assertEquals("http://192.168.1.10:11434/v1/chat/completions", aiCompletionsUrl(AiProvider.OLLAMA, "192.168.1.10:11434/"))
        assertEquals("https://ai.example.org/api/v1/chat/completions", aiCompletionsUrl(AiProvider.OTHER, "https://ai.example.org/api/v1"))
        assertNull(aiCompletionsUrl(AiProvider.OLLAMA, " "))
    }

    @Test fun readyWhenItHasWhatItNeeds() {
        assertFalse(AppSettings().aiReady)  // no key
        assertTrue(AppSettings(aiKey = "k").aiReady)
        assertFalse(AppSettings(aiKey = "k", aiTitles = false).aiReady)
        assertFalse(AppSettings(aiProvider = AiProvider.OLLAMA, aiModel = "llama3.2").aiReady)  // no address
        assertTrue(AppSettings(aiProvider = AiProvider.OLLAMA, aiModel = "llama3.2", aiAddress = "nas:11434").aiReady)  // no key needed
        assertFalse(AppSettings(aiKey = "k", aiModel = " ").aiReady)
        assertEquals(AiProvider.OPENROUTER, AiProvider.parse(null))
        assertEquals(AiProvider.GROQ, AiProvider.parse("GROQ"))
    }

    @Test fun theAnswerOrWhatWentWrongInTheServicesName() {
        val ok = """{"choices":[{"message":{"role":"assistant","content":"Hello"}}]}"""
        assertEquals("Hello", ChatApi.answer(AiProvider.MISTRAL, 200, true, ok))
        fun error(body: String, code: Int = 401) = runCatching { ChatApi.answer(AiProvider.ANTHROPIC, code, false, body) }.exceptionOrNull()
        assertEquals("Anthropic: invalid x-api-key", error("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""")?.message)
        assertEquals("Anthropic: model not found", error("""{"error":"model not found"}""", 404)?.message)
        assertEquals("Anthropic: HTTP 502", error("<html>bad gateway</html>", 502)?.message)
        assertTrue(runCatching { ChatApi.answer(AiProvider.GROQ, 200, true, """{"choices":[]}""") }.exceptionOrNull() is ApiException)
    }

    @Test fun theRequestIsSmall() {
        val p = ChatApi.payload("gpt-4.1-mini", "sys", "hi")
        assertTrue(p.contains("\"model\":\"gpt-4.1-mini\""))
        assertTrue(p.contains("\"max_tokens\":300"))
        assertTrue(p.contains("\"role\":\"system\",\"content\":\"sys\""))
    }
}
