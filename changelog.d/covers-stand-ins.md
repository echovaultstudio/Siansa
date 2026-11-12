### Fixed
- **A cover that showed as Navidrome's blue disc (or an artist as a star) in lists while Now playing had the real one.** Navidrome sends that stand-in when it doesn't have a cover yet, and Siansa kept it for good. Siansa now shows it but never keeps it, and asks again the next time the cover is needed. Stand-ins already kept are removed once, by themselves.
- **A cover replaced on Emby or Jellyfin kept showing the old one**, and covers in the notification and the car were kept apart from the ones in lists there. All screens now share the same covers on every server.

### Changed
- **Sync all also refreshes covers.** It checks the covers kept on the phone: missing ones are fetched fresh and changed ones replaced, without clearing the image cache.
