### Fixed
- **Beat-matched blends still began a touch out of step.** The phone reports a wrong position for a few seconds
  after a jump; the silent copy of the song going out, and the next song after it comes in, are now judged only
  once the position is real. The copy starts 14 seconds ahead instead of 8 and is nudged into step in one measured
  move rather than chased.
- **The log said a song started playing minutes after it was chosen** when it had run straight on from the last
  one; it no longer counts from the wrong moment.
