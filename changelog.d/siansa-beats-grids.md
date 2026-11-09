### Added
- **Beat grids from Siansa Beats (Siansa Dev, an experiment).** Siansa Dev takes each song's loudness and fades as the Siansa plugin measured them, and its tempo, beats, bars and phrases from Siansa Beats, through the Siansa Beats (dev) plugin for AudioMuse-AI.
- **A playlist's own DJ mix (Siansa Dev).** On a playlist, slide Shuffle onto Play, as on an album, for a DJ mix of just its songs, in the order that blends best.

### Changed
- **Crossfade: Smart decides everything, Fixed is yours to set (Siansa Dev).** With Smart there's nothing to set. Fixed has its length and, in DJ radio and DJ mixes, the blend length in bars. Beat-matched blends happen in DJ radio and DJ mixes only, with either; an album playing in order always stays gapless.
- **Blends stay where a song's beat is (Siansa Dev).** With Siansa Beats, a blend is laid where each song's beat locks onto its grid, and only when 80 % of its beats are there: tracks cut from a mixed CD no longer clash with the DJ's own changeovers.
- **Smart DJ blends pick how to blend (Siansa Dev).** With Siansa Beats' readings, each blend in DJ radio and DJ mixes is chosen for the two songs: a long 32-bar blend when tempos are close, keys mix and an end has room without bass; a drop swap that brings the next song in so its drop lands where the old one goes; a filter fade when the energy goes down or the keys only half mix; a quick 4-bar swap when keys clash, the beat isn't sure or you just moved in the song; an echo out when the tempos are too far apart (the two never play together: the old song echoes out on a bar line and the next drops in on its first downbeat); else the usual bass swap, and never the same twice running when another fits.
- **"Bad blend" button (Siansa Dev).** During a DJ blend and for a minute after, a button over the cover marks it as bad: it goes to the log and to Siansa Beats' list.

- **After a quiet ending, the next song builds up (Siansa Dev).** It no longer comes in at full level over an old song's quiet end: it rises slowly to its full level at its first change (a phrase, its bass coming in, or its drop).

### Fixed
- **A big playlist's DJ mix was only shuffled.** Asking AudioMuse-AI for the tempo and key of 250 songs at once was refused; they're now asked in smaller batches.
