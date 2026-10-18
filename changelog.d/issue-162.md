### Fixed
- **Songs no longer get stuck after switching to Opus on a slow connection.** When the connection was too slow
  for the original file, Musix switched the song to Opus but then waited, sometimes for good, before it played
  on. Navidrome now starts the converted song right where it was, and jumping around in a song that plays as
  Opus is instant too.
- **Animated album covers no longer show blank in lists now and then.**
- **Now playing uses less battery.** The seek bar updates five times a second instead of on every screen frame.
- **Covers that Navidrome failed to send no longer stay blank.** Navidrome sometimes answers with an error
  instead of a picture (for example when shrinking a big GIF); Musix kept that answer as the cover. Now it asks
  again, and a cover that can't be read is fetched afresh next time.
- **A changed stream quality applies to every song.** A stopped song kept its old quality when played again.
