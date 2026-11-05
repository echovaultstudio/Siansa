### Added
- **More of Siansa on the TV.** The TV app's Home now has Today's mix, your mixes, radios, Rediscover, Jump back in and
  the other rows of your phone's Home, in the same order. The Library has liked songs and genres. Songs have Play next,
  Add to queue, Like and song radio. Now playing has Like and Up next.
- **Favourites on the TV**: favourite albums and artists in the Library, and a heart on album and artist pages.
- **Artist pages on the TV**: top songs, albums and similar artists, with Play, Shuffle and Artist radio; artists,
  genres and albums on other pages open them.
- **Several servers and users on the TV**: switch between them, add one, and pick whose music and which libraries
  show, under Settings.
- **Lyrics and song radio in Now playing on the TV**, over the cover blurred as on the phone, and today's daily mixes
  on Home.
- **AudioMuse's song path and song alchemy on the TV**.
- **Animated covers on the TV**, in Now playing and on album pages, and playlists' own covers from the server.
- **The TV opens Now playing when a song starts** (it can be switched off under Settings).
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
- Dolby Atmos songs one after another on the TV (a DJ mix) now keep the output to the receiver open from one to the
  next, so there's no gap while the receiver finds the signal again, and a song that stalls there is started again
  instead of switching to a converted stream without Atmos.
- Songs whose file says it's far longer than it is (some Dolby Atmos DJ mixes in `.m4a`) stopped at their end and
  didn't load the next song in time, so a gapless mix had gaps, and the end of a song could switch to a converted
  stream without Dolby Atmos. Siansa now goes by the song's length from your server, on the phone too.
- The TV's Now playing: the cover is blurred much more, on TVs without Android 12 too (an NVIDIA SHIELD showed it
  sharp), the page is calmer, with the other buttons round under the controls and their names shown as the remote
  reaches them, and it runs more smoothly.
- Buttons on the TV that looked joined to the one beside them when the remote was on them.
- The TV's tabs along the top go back to that tab's own page: Library from an album opened in it shows the Library
  again, not the album.
- The TV's Library and Home rows show six covers across instead of four or five, and the lyrics in Now playing can be
  scrolled with the remote.
- Pausing for most of a second when making a mix (Today's mix, a daily mix), on the phone too; and the TV uses less memory for covers,
  giving them back when the TV runs low (an NVIDIA SHIELD kept warning of low memory). The sleep timer is gone from
  the TV app.
- Typing on the TV: text fields open Android's own text box with the keyboard up (Search as soon as you reach it), and
  take what's typed or pasted on a phone with the Google TV app.
- Siansa's settings showing the Siansa plugin as 0.3.3 whatever version was installed (update the plugin to 0.5.6).
