# Install Siansa and report a problem

> **Siansa is in beta.** It's still in active development. It has been tested a lot with Navidrome to iron out most
> bugs, but it isn't perfect yet, so expect some. If you find one, please report it from the app
> (Settings › Help › Report a problem). Thank you!

Siansa is a music player for your own Navidrome or Emby server, with mixes made for the moment.
Problems and ideas go in the [Siansa repository on GitHub](https://github.com/echovaultstudio/Siansa/issues), where the code is too.

Website and sign-up: <https://siansa.app>

How to use Siansa, every setting, and how it all works: [the docs](README.md)

What changed in each version: [CHANGELOG.md](../CHANGELOG.md)

## What you need

- A phone with **Android 8.0 or newer**.
- Your own **Navidrome** or **Emby** server that your phone can reach (at home on Wi-Fi is fine; away from home
  use a VPN such as WireGuard or Tailscale, or your own secure address).
- **[AudioMuse-AI](https://github.com/NeptuneHub/AudioMuse-AI) 3.6.4 or newer**, connected to that server, with
  text search (CLAP) turned on. It makes the mixes and the "search by sound".
- The **Siansa Analysis plugin** installed in AudioMuse-AI (steps below). It's needed for volume levelling,
  smooth crossfades and "similar albums and artists". Without it those features don't work.
- Optional: an AI service for mix titles written by an AI model: an [OpenRouter](https://openrouter.ai) key, another
  service's key, or Ollama on your network.

## Install Siansa

Pick one. Both give you the same app, and updates arrive the same way.

**Google Play.** Use the invite link we email you, tap **Become a tester**, then install Siansa from Play.
Use the Google account whose email you gave us, and stay in the test for 14 days.

**F-Droid.** In the F-Droid app, go to **Settings › Repositories › +** and add
`https://siansa.app/fdroid/repo` (the page also shows a QR code and the repo's fingerprint). Then search for
Siansa and install it.

F-Droid needs no invite. For Google Play, [ask for a place in the beta](https://siansa.app/#join).

**Had Siansa from the earlier private F-Droid address?** Remove that repository, add the open one above, then
uninstall Siansa once and install it again: the app has a new signing key since the project went public, and
F-Droid can't update across a key change. You'll need to enter your servers again afterwards.

## Connect your servers

Open Siansa, go to **Settings**, enter these, use **Test** next to each, then **Save**:

1. **Your music server**: Navidrome or Emby. Address (for example `http://192.168.1.10:4533`), user name, password.
2. **AudioMuse-AI**: its address (for example `http://192.168.1.10:8000`) and its API token if you use one.
   If your AudioMuse-AI serves several music servers, also fill in **Server name in AudioMuse**.
3. Optional: **AI**: the service, its key and model.

## Install the plugin in AudioMuse-AI

1. In AudioMuse-AI, open **Plugins › Repositories** and add this address:
   ```
   https://raw.githubusercontent.com/echovaultstudio/siansa-audiomuse-plugin/main/manifest.json
   ```
2. On the **Catalog** tab, install **Siansa Analysis** and apply the restart.
3. Open **Plugins › Siansa Analysis › Settings** and use **Measure all songs** to measure your library.
   A big library takes hours or days, and AudioMuse-AI pauses its own jobs while a round runs.
4. In Siansa, go to **Settings › Crossfade and levelling** and choose the plugin.

## Report a problem

1. [Open a bug report](https://github.com/echovaultstudio/Siansa/issues/new?template=bug_report.yml) and fill in the short form.
2. Look at the [open issues](https://github.com/echovaultstudio/Siansa/issues) first. Someone may have reported it already: add a 👍 or a comment instead.

The most useful reports say what you did, what you expected and what happened. A log helps a lot:
in Siansa, go to **Settings › Help › Show the log**, then **Share**, and attach the file, or paste the part around the problem.

**Before you post anything**
- Never paste passwords, tokens or API keys. Siansa's log never has them, but check anyway.
- The log can contain your server's address. This page is public: replace it with `my-server` first.
- Don't post personal information about other people.

## Suggest an idea

[Open a feature request](https://github.com/echovaultstudio/Siansa/issues/new?template=feature_request.yml). Please keep it to one idea per issue.

## Questions or something private

Email <help@siansa.app>. For privacy questions: <privacy@siansa.app>.

## What happens next

We read every issue. Bugs that we can reproduce are fixed in the next version, and the issue is closed
with the version number. Some ideas will wait or won't fit; we'll say so.

## Be kind

Testers give their time for free, and so do we. Be polite and stay on topic. Spam and abuse are removed.
