### Fixed
- **Casting an Apple Lossless (ALAC) album ended the cast.** Songs in formats Chromecast and Google TV can't play,
  such as ALAC and other .m4a files, now go to the TV as MP3 320; FLAC, MP3, Ogg, WAV and AAC still go as they are.
- **After one failed start, the TV kept Google's plain screen until Siansa was restarted.** Only that one retry uses
  it now; the next time you cast, Siansa's own TV screen is tried again.
- **Songs in the queue showed blank while casting a long queue, and could stop the music.** Songs the TV doesn't
  describe are filled in from your queue on the phone, and a song nobody can name is left out instead of being
  played as an empty song.
- **A short network drop moved the music to the phone and straight back to the TV, losing most of the queue.** The
  music now stays on the TV while Cast reconnects, and only comes back to the phone if the TV doesn't return.
