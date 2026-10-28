# Maintaining the docs

These pages are the user documentation for Siansa. They follow the newest beta.

## The rule

**Every change a user would notice updates these pages in the same release.** A new feature, a new or renamed
setting, a changed default, a new requirement, a new service Siansa talks to: each one goes into the page it
belongs to, and into [API](api.md) or [Privacy](privacy-and-logs.md) when it changes what Siansa sends or keeps.

A pull request that adds a `### Added` or `### Changed` changelog note also updates these pages, in the same pull
request, and says which page in its description.

## Where things go

| Change | Page |
|---|---|
| Setup, requirements, installing | [Getting started](getting-started.md) |
| Servers, users, libraries, source filter | [Servers, users and libraries](servers-and-users.md) |
| AudioMuse-AI or the plugin | [AudioMuse-AI](audiomuse.md), [API](api.md) |
| Home, mixes, vibes, radios | [Home and your mixes](home-and-mixes.md) |
| Song radio, similar, Song path, Flow, dice | [Discovery](discovery.md) |
| Playing, quality, cache, downloads, fades, EQ, lyrics, casting | [Playback](playback.md) |
| Reports, Time Machine, scrobbling | [Your listening](your-listening.md) |
| AI, the moment | [AI features](ai.md) |
| Audiobooks, podcasts, Audiobookshelf | [Audiobooks and podcasts](books.md) |
| Any settings page | [Settings](settings.md) |
| How something is worked out | [How it works](how-it-works.md) |
| Something users run into | [Troubleshooting](troubleshooting.md) |

## Writing

- Plain, short English, for the app's users. Name screens and settings exactly as the app shows them, with `›`
  between steps: **Settings › Playback › Keep playing**.
- Say what something does and when it's useful, then the details.
- No passwords, tokens, private addresses or log excerpts with personal data.

## docs.siansa.app

These Markdown files are written so they can be published as a site later (for example with MkDocs or GitHub
Pages) at docs.siansa.app, without rewriting them.
