### Added
- **More of Siansa on the TV.** The TV app's Home now has Today's mix, your mixes, radios, Rediscover, Jump back in and
  the other rows of your phone's Home, in the same order. The Library has liked songs and genres. Songs have Play next,
  Add to queue, Like and song radio. Now playing has Like, Up next and a sleep timer.
- **Settings on the TV**: servers, library sync, playback, streaming quality, mixes, scrobbling, colours and help,
  the same pages as on the phone.
- **Report a problem from the TV.** Debug logs are on in the TV app from the start, and a report sends the log the
  way the phone does.

### Fixed
- Text on the TV that was too dark to read.
- The TV's Home and Library jumping and losing the remote's place while the library synced.
- 5.1 and 7.1 songs (Dolby or DTS decoded on the device, or multichannel FLAC) played without their centre and
  surround channels, often missing the vocals. They're now folded into stereo, on the phone too.
- Dolby Atmos songs in `.m4a` or `.mp4` files on the TV: they now always stream as the original file while passthrough
  is on, and a song the receiver can't take after all plays decoded on the TV instead of failing.
