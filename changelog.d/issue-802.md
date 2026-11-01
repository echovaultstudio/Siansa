### Fixed
- **Drum and bass was never beat-matched in DJ blends.** The Siansa plugin read its tempo at two thirds (113 BPM for
  170), with the beats off the music. Plugin 0.4.2 reads it right and measures every song's beats again in the
  background; update it in AudioMuse-AI.
