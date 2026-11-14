# Getting started

## What you need

| | |
|---|---|
| **Phone** | Android 8.0 or newer. |
| **Music server** | Navidrome (or another OpenSubsonic server: Gonic, Airsonic, Ampache…), Jellyfin (10.9 or later) or Emby, that your phone can reach. At home on Wi-Fi is fine. Away from home, use a VPN such as WireGuard or Tailscale, or your own secure address. Files on the phone are listed as "coming soon". |
| **AudioMuse-AI** (recommended) | [AudioMuse-AI](https://github.com/NeptuneHub/AudioMuse-AI) 3.6.4 or newer, connected to your music server, with text search (CLAP) on. It powers the mixes, radios, search by sound and more. See [AudioMuse-AI](audiomuse.md). |
| **[Siansa Analysis plugin](https://github.com/echovaultstudio/siansa-beats)** (recommended) | Installed in AudioMuse-AI ([how](audiomuse.md#install-the-siansa-analysis-plugin)). Needed for loudness levelling from measured audio, smart crossfades, DJ blends that keep to the beat, and similar albums and artists. |
| **An AI service** (optional) | For mix titles and sound searches written by an AI model: OpenRouter, OpenAI, Anthropic, Google Gemini, Mistral, Groq, or Ollama on your own network. See [AI features](ai.md). |
| **Last.fm or Koito** (optional) | Where Siansa sends your listens (scrobbling) and reads your history back from. [Last.fm](https://www.last.fm) with your own API key, or [Koito](https://github.com/gabehf/Koito), a ListenBrainz-compatible history server you run yourself. They keep your full listening history, which powers **Time Machine**, **Your month and year** and your history. Siansa Beats keeps it too, when you scrobble there. Set them up in **Settings › Scrobbling & history**. See [Your listening](your-listening.md#scrobbling). |

Siansa works as a plain player with only a music server. Everything "smart" needs AudioMuse-AI. Without Siansa Beats, Last.fm or Koito, Time Machine and your history use only what Siansa and your server have recorded.

## Install

Pick one. Both give you the same app, and updates arrive the same way.

- **Google Play**: use the invite link we email you, tap **Become a tester**, then install Siansa from Play.
  Use the Google account whose email you gave us.
- **F-Droid**: in the F-Droid app, go to **Settings › Repositories › +** and add the repository address from your
  invite. Then search for Siansa and install it.

Not on the list yet? [Ask for an invite](https://siansa.app).

## First setup

1. **Where's your music?** Pick your server type: OpenSubsonic (Navidrome and others), Jellyfin or Emby.
2. **Smart extras.** Choose **Add Siansa Beats** if you have it, or **Not now**. You can add it later in
   Settings › Connections.
3. **Connect your servers.** Enter your server's address (for example `http://192.168.1.10:4533`), user name and
   password. Tap **Test**, then **Save**. If you added Siansa Beats, enter its address too (and its key, if
   it asks for one). AudioMuse-AI's address and login are optional, below it.
4. **Library sync.** Siansa copies your library's list of artists, albums and songs to the phone, so browsing
   and search are instant and mixes build quickly. The first sync of a large library takes a few minutes. Only
   the list is copied, never the music files.

That's it: Home fills with your mixes and recent music.

## Updating

Google Play and F-Droid update Siansa by themselves. **Settings › About › What's new** shows what changed in the version
you have; tap it to see every version's changes.

## Next

- Several servers or users? See [Servers, users and libraries](servers-and-users.md).
- Set up the smart parts: [AudioMuse-AI and the Siansa plugin](audiomuse.md).
- Keep your history and use Time Machine: [Your listening](your-listening.md).
