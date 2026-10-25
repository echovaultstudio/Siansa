### Fixed
- **Dolby (E-AC3) songs weren't gapless.** Their files claim to be far longer than they are, so the next song wasn't loaded ahead and started after a short silence. Siansa now loads ahead by size for these songs, so the next one follows straight on.
