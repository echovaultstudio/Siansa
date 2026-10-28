# AI features

All AI in Siansa is **optional**. The "smart" features (mixes, radios, search by sound) come from AudioMuse-AI on
your own server, which isn't a chatbot: it analyses how your music sounds. The only outside AI is OpenRouter, and
only if you add a key.

## OpenRouter: mix titles and sound searches

**Settings › Connections › OpenRouter**: an API key from [openrouter.ai](https://openrouter.ai) and a model
(`google/gemini-2.5-flash` by default). **AI titles and sound searches** turns it on or off.

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

The **Use the moment** panel (in **Settings › Daily Vibe settings**) lets today's mixes suit what's going on
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
