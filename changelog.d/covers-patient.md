### Fixed

- **Download all covers gave up on artist photos too soon.** A server that fetches photos a few at a time (Navidrome,
  from Apple Music or Deezer) is now asked again every half minute while photos keep coming, for up to ten minutes.
  The result says how many artists your server has no photo for yet, apart from covers it has no picture for.
