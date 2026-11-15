### Fixed

- **Download all covers missed many artist photos.** Artists with no album of their own (on a compilation, or
  featured on a song) were left out, and a photo your server was still fetching was counted as "no picture". Every
  artist credited on a song is now included, and photos still being fetched are asked for again before the run ends.
