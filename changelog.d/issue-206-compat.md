### Fixed
- **Better at talking to Navidrome**, following its rules more closely:
  - Musix asks Navidrome which extras it has and only uses those.
  - Logins are safer: each request gets a fresh one-time code.
  - A clear message when the password is wrong, something was removed, or your user isn't allowed to do something.
  - If your Navidrome user can't log in the usual way (LDAP or proxy login), Musix now tries the other way.
  - Long playlists save even behind a proxy.
  - Artist photos come through Navidrome like every other cover, so they're saved by "Download all covers" too.
  - Liked songs, search and Home lists keep to the libraries you picked for the sync.
