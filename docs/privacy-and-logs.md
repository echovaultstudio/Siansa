# Privacy, logs and reporting problems

## Privacy in short

Siansa has no account with us, no ads, no analytics and no tracking. We don't run a server that receives your
data. Everything Siansa sends goes to services **you** set up or switch on: your music server, AudioMuse-AI, and
optionally Audiobookshelf, the AI service you pick (OpenRouter, OpenAI, Anthropic, Google Gemini, Mistral, Groq, or Ollama
or another on your own network), Last.fm, ListenBrainz or Koito, Open-Meteo (weather) and Deezer (artist photos). What each
one gets is in [API › Other services](api.md#other-services).

Your settings, passwords (encrypted with Android's keystore), history, downloads and caches stay on your phone.
Uninstalling Siansa removes them.

The full privacy policy: <https://siansa.app/privacy.html>

## The log

**Settings › Help › Debug logs**. The log helps find problems. While it's on, it writes
what Siansa does, including every request to your servers in full: addresses, server IPs, what was asked and how
it answered.

- It **never** holds passwords, tokens or API keys. They're replaced with `•••`.
- It **does** hold your servers' addresses and what you played.
- It stays on the phone unless you send a report. **Show the log** (in Help) has **Save logs**, which writes a text file you can upload anywhere; **Share** and **Copy** do what
  they say; **Problems** shows only errors and warnings.

## Report a problem

**Settings › Help › Report a problem** is the quickest way to tell us something's wrong:

1. Turn on **Debug logs** (in Help, or on the report page).
2. **Make the problem happen a few times**, so the log shows it more than once.
3. Say **what happened** and **what you expected**, tick **I made the problem happen a few times**, and **Send report**.

The log always goes with a report: the report can't be sent while debug logs are off or the log is empty.

What happens next:

- Your words, the app version and your phone model are posted as an issue in the public
  [Siansa repository](https://github.com/echovaultstudio/Siansa/issues), with a report id.
  **The log isn't public.**
- The log is kept privately, on our server and in a private repository only the Siansa team can open. It may be
  read with the help of AI tools to find the problem, is used only to fix it, and is deleted afterwards.
- When a fix is released, the issue is closed with the version that has it.

Before you send, try to make the problem happen once more with the log on, so the log shows it.

## Backups

Backups (**Settings › Backup**) are files you save and keep yourself. They never hold passwords, tokens or API
keys, so after restoring on a new phone you sign in to your servers again.
