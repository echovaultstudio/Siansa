### Fixed
- **A newly added album could take several Navidrome scans to show.** When Navidrome hadn't run a new scan, the
  check at app start skipped the sync entirely, even if music had been added. It now looks at Navidrome's newest
  albums first and syncs when one isn't on the phone. Each sync also logs which new albums it found.
