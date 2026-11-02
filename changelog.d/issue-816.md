### Fixed
- **DJ blends stuttered or came in late.** The next song is now loaded a minute ahead, so it no longer starts a blend cold (it began up to 400 ms late and off the beat). A song sped up or slowed down for a blend is resampled, like a DJ's pitch fader, instead of stretched in time, which could stutter on drums.

### Added
- **Keep the key** (Settings › Crossfade and levelling › Beat-matched blends, off): keeps the next song's key through a blend by stretching the sound instead.
