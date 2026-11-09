### Added
- **Beat grids from Siansa Beats (Siansa Dev, an experiment).** Siansa Dev takes each song's loudness and fades as the Siansa plugin measured them, and its tempo, beats, bars and phrases from Siansa Beats, through the Siansa Beats (dev) plugin for AudioMuse-AI.
- **A playlist's own DJ mix (Siansa Dev).** On a playlist, slide Shuffle onto Play, as on an album, for a DJ mix of just its songs, in the order that blends best.

### Changed
- **Crossfade: Smart decides everything, Fixed is yours to set (Siansa Dev).** With Smart there's nothing to set. Fixed has its length and, in DJ radio and DJ mixes, the blend length in bars. Beat-matched blends happen in DJ radio and DJ mixes only, with either; an album playing in order always stays gapless.
- **Blends stay where a song's beat is (Siansa Dev).** With Siansa Beats, a blend is laid where each song's beat locks onto its grid, and only when 80 % of its beats are there: tracks cut from a mixed CD no longer clash with the DJ's own changeovers.
- **"Bad blend" button (Siansa Dev).** During a DJ blend and for a minute after, a button over the cover marks it as bad: it goes to the log and to Siansa Beats' list.

### Fixed
- **A big playlist's DJ mix was only shuffled.** Asking AudioMuse-AI for the tempo and key of 250 songs at once was refused; they're now asked in smaller batches.
