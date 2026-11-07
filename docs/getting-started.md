# Getting started

## What you need

Siansa plays with a music server alone. For the best of it, set up all four:

| | |
|---|---|
| **A music server** | [Navidrome](https://www.navidrome.org) (or another OpenSubsonic server), [Jellyfin](https://jellyfin.org) 10.9 or newer, or [Emby](https://emby.media), that your phone can reach. Away from home, use a VPN such as WireGuard or Tailscale, or your own secure address. |
| **AudioMuse-AI** | [AudioMuse-AI](https://github.com/NeptuneHub/AudioMuse-AI) 3.6.4 or newer, with text search on, and the [Siansa plugin](audiomuse.md#install-the-siansa-plugin) in it. It powers the mixes, radios, search by sound, smart crossfades and DJ blends. See [AudioMuse-AI](audiomuse.md). |
| **An AI service** | [OpenRouter](https://openrouter.ai) or a similar service (OpenAI, Anthropic, Gemini, Mistral, Groq, or Ollama at home), for mix titles and sound searches written for the moment. A small, cheap model is plenty. See [AI features](ai.md). |
| **Last.fm or Koito** | Where your listens are kept: [Last.fm](https://www.last.fm), or [Koito](https://github.com/gabehf/Koito) on your own server. They keep your whole history, for **Time Machine** and **Your month and year**. See [Your listening](your-listening.md#scrobbling). |

Plus Android 8.0 or newer on your phone, or Android TV or Google TV for [the TV app](tv.md).

## Install

- **Google Play** (in testing): [join Siansa's testers](https://siansa.app/#join), then install Siansa from Google
  Play with the same Google account. On a TV, search for Siansa in the TV's Play Store.
- **F-Droid**: in the F-Droid app, **Settings › Repositories › +**, add `https://siansa.app/fdroid/repo`, then install
  Siansa.

Both update by themselves. **Settings › About › What's new** shows what changed.

## First setup

1. **Where's your music?** Pick OpenSubsonic (Navidrome and others), Jellyfin or Emby.
2. **Smart extras.** **Add AudioMuse-AI** if you have it, or **Not now**.
3. **Connect.** Your server's address (`http://192.168.1.10:4533`), user name and password, then **Test** and
   **Save**; the same for AudioMuse-AI.
4. **Library sync.** Siansa copies the list of your artists, albums and songs to the phone (never the files), so
   browsing is instant. A big library takes a few minutes the first time.

Home then fills with your mixes. Add the AI service and Last.fm or Koito under **Settings › Connections** and
**Settings › Scrobbling and history** whenever you like.

## Next

- [Home and your mixes](home-and-mixes.md): what's on Home and how to make it yours.
- [Siansa on your TV](tv.md): the TV app, signed in from your phone in a few seconds.
- [Servers, users and libraries](servers-and-users.md): several logins, or the family's music together.
