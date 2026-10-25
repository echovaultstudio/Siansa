### Fixed
- **Navidrome's log filled with "missing parameter: 'u'" warnings.** Siansa checks whether your server can be reached without signing in, and that check asked Navidrome's API. It now just knocks on the server's front page, which Navidrome answers without a warning.
