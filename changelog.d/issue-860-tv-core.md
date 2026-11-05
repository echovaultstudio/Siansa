### Added
- **More of Siansa on the TV.** The TV app's Home now has Today's mix, your mixes, radios, Rediscover, Jump back in and
  the other rows of your phone's Home, in the same order. The Library has liked songs and genres. Songs have Play next,
  Add to queue, Like and song radio. Now playing has Like, Up next and a sleep timer.
- **Favourites on the TV**: favourite albums and artists in the Library, and a heart on album and artist pages.
- **Artist pages on the TV**: top songs, albums and similar artists, with Play, Shuffle and Artist radio; artists,
  genres and albums on other pages open them.
- **Several servers and users on the TV**: switch between them, or add one, under Settings.
- **Settings on the TV**: servers, library sync, playback, streaming quality, mixes, scrobbling, colours and help,
  the same pages as on the phone.
- **AudioMuse on the TV**: Your sound and Discover for you among your mixes, sound radios, similar songs, and songs
  that sound like a few words you type or say in Search.
- **Skipping within a song on the TV**: rewind and fast forward on the remote work anywhere, and the progress bar in
  Now playing takes left and right for remotes without them.
- **Report a problem from the TV.** Debug logs are on in the TV app from the start, and a report sends the log the
  way the phone does.

### Fixed
- Text on the TV that was too dark to read, and buttons cut off at the bottom of album pages: an album's songs now
  come straight after its buttons.
- The TV's Home and Library jumping and losing the remote's place while the library synced.
- 5.1 and 7.1 songs (Dolby or DTS decoded on the device, or multichannel FLAC) played without their centre and
  surround channels, often missing the vocals. They're now folded into stereo, on the phone too.
- Gaps between Dolby Atmos songs sent to the receiver (a DJ mix, a live album): the output now stays open from one
  song to the next.
- Dolby Atmos songs in `.m4a` or `.mp4` files on the TV: they now always stream as the original file while passthrough
  is on, and a song the receiver can't take after all plays decoded on the TV instead of failing.
