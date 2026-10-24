# Changelog

Every version of Siansa, newest first. Each section is also shown as the notes on its GitHub release.
Changes add a note in `changelog.d/` instead of editing this file; the release adds the section here.

## 0.29.0beta — 2026-10-24

### Fixed
- **Mixes on Emby said "Navidrome didn't recognise AudioMuse's tracks".** When AudioMuse knows your music under another server's ids, Siansa now finds each track in your library by its title and artist.

### Added
- **Report a problem from the app.** Under Settings › Report a problem, say what happened and what you expected, and send it with your log to the Siansa beta team. The log stays private; a short note with your words is posted in the beta testers repo.

### Changed
- **Logs always record everything.** The "Detail for" choices are gone: when "Keep a log" is on, all detail is kept. Passwords, tokens and API keys are still removed.
- **AudioMuse and OpenRouter are set up once.** They're the same for every account and every music server, so changing server or account no longer asks for them again.

## 0.28.1beta — 2026-10-24

### Added
- **Welcome to the Siansa beta.** A music player for your own Navidrome or Emby server, with mixes made for the moment. The smart parts (mixes made for you, Vibes, sounds-like, Song path, Your sound) come from AudioMuse-AI with the **Siansa Analysis** plugin installed.
