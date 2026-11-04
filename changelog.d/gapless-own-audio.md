### Fixed
- **Gapless casting played no sound on some TVs (an NVIDIA SHIELD), though the songs showed.** Siansa's TV screen now
  plays the phone's stream itself instead of through Google's Cast player, tries WAV if the TV won't start FLAC, and
  hands the TV the queue with a short note if neither starts, instead of trying again for ever in silence.
- **Disconnecting headphones paused a gapless cast.** The sound is on the TV, so it now plays on.
