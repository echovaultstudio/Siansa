# AI features

All AI in Siansa is **optional**. The "smart" features (mixes, radios, search by sound) come from AudioMuse-AI on
your own server, which isn't a chatbot: it analyses how your music sounds. The only outside AI is the AI service you
pick for mix titles and sound searches, and only if you set one up.

## Mix titles and sound searches

**Settings › Servers and services › AI**: pick the service, then its key and a model. Picking a service fills in its usual
model; any model it offers works. **AI titles and sound searches** turns it on or off, and **Test** asks it to say
hello.

| Service | What it needs |
|---|---|
| **OpenRouter** (the default) | An API key from [openrouter.ai](https://openrouter.ai): many models from one account. Usual model `google/gemini-2.5-flash`. |
| **OpenAI** | An API key from platform.openai.com. Usual model `gpt-4.1-mini`. |
| **Anthropic** | An API key from console.anthropic.com. Usual model `claude-haiku-4-5`. |
| **Google Gemini** | An API key from Google AI Studio. Usual model `gemini-2.5-flash`. |
| **Mistral** | An API key from console.mistral.ai. Usual model `mistral-small-latest`. |
| **Groq** | An API key from console.groq.com. Usual model `llama-3.3-70b-versatile`. |
| **Ollama** | Ollama on your own network: its address (like `192.168.1.10:11434`) and a model you've pulled. No key, and nothing leaves your network. |
| **Other** | Any other service with an OpenAI-style API (LM Studio, LocalAI, vLLM…): its address, a key if it asks for one, and a model. |

A small model is plenty: the answers are a title and a line or two.

With it on, for each vibe or Flow description the AI writes:

- a **title** for the mix,
- a **sound search** for AudioMuse-AI (what the music should sound like),
- up to three of AudioMuse-AI's **sound concepts** to steer the search towards or away from.

**What's sent:** the vibe's guidance or your Flow words, the day and time, the kind of audio device (for example
"car Bluetooth"), what the Situation panel allows (see below), and the titles and artists of your last 20 plays.
Your library is never sent.

**Without AI** (or when it's unavailable, or an answer uses one of the vibe's "never use these words"), the
vibe's fallback sound search is used, or your Flow words as they are. You still get a mix; only the title is
plainer.

## Situation

The **Use the moment** panel (in **Settings › Mixes and vibes**) lets today's mixes suit what's going on
around you. Each part is switched on by itself, and is read once when a mix is made, never in the background:

| Part | How it's read |
|---|---|
| **Weather** | Rain, sun, cold, sunset. From Open-Meteo, with your approximate location rounded to about 10 km, only to ask it. The location isn't kept. Off until you switch it on. |
| **Movement** | Sitting, walking or running, from the phone's step counter. Stays on the phone. Off until you switch it on. |
| **Light** | A dark room or daylight, from the light sensor. Off until you switch it on. |
| **Charging** | Whether the phone is charging, or its battery is low. Off until you switch it on. |
| **Listening device** | Phone speaker, headphones or Bluetooth (a car suggests driving). On by default. |

With AI, the situation goes into the AI's prompt as one line ("light rain, 9 °C, evening; walking; in a dark
room"). Without AI, it nudges AudioMuse-AI's search directly: rain and dark rooms lean calm and acoustic, sun and
heat upbeat, running energetic.
