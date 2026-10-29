package app.siansa.data

/**
 * The AI service that writes mix titles and sound searches (issue #665). Each speaks the same "chat completions"
 * API, so one client talks to all of them: only the address, the login and the model name differ. [address] is
 * the provider's own; empty for one on your network or another service, whose address is typed in. [defaultModel]
 * is a suggestion, filled in when the provider is picked; any model the provider offers works.
 */
enum class AiProvider(
    val label: String,
    val address: String,
    val defaultModel: String,
    val needsKey: Boolean = true,
    val hint: String,
) {
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "google/gemini-2.5-flash",
        hint = "Many models from one account and key, at openrouter.ai."),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini",
        hint = "An API key from platform.openai.com."),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1", "claude-haiku-4-5",
        hint = "An API key from console.anthropic.com."),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash",
        hint = "An API key from Google AI Studio (aistudio.google.com)."),
    MISTRAL("Mistral", "https://api.mistral.ai/v1", "mistral-small-latest",
        hint = "An API key from console.mistral.ai."),
    GROQ("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile",
        hint = "An API key from console.groq.com."),
    OLLAMA("Ollama", "", "llama3.2", needsKey = false,
        hint = "Ollama on your own network: its address, like 192.168.1.10:11434. No key, and nothing leaves your network."),
    OTHER("Other", "", "", needsKey = false,
        hint = "Any other service with an OpenAI-style API, like LM Studio, LocalAI or vLLM: its address, and a key if it asks for one.");

    /** The address is typed in (on your network, or another service), not the provider's own. */
    val ownAddress: Boolean get() = address.isEmpty()

    companion object {
        fun parse(value: String?): AiProvider = entries.firstOrNull { it.name == value } ?: OPENROUTER
    }
}

/**
 * The full address of the chat completions call for [provider], with [typed] as the address of one on your network
 * or another service ("192.168.1.10:11434", "http://host:1234/v1"); null when there's no address to call. A typed
 * address without a scheme gets http://, and one without a path gets /v1, as Ollama and LM Studio serve it.
 */
fun aiCompletionsUrl(provider: AiProvider, typed: String): String? {
    val base = if (provider.ownAddress) {
        val t = typed.trim().trimEnd('/')
        if (t.isEmpty()) return null
        val withScheme = if (t.contains("://")) t else "http://$t"
        val path = withScheme.substringAfter("://").substringAfter('/', "")
        if (path.isEmpty()) "$withScheme/v1" else withScheme
    } else {
        provider.address
    }
    return "$base/chat/completions"
}
