### Fixed
- **Stop didn't stop Download all covers.** It kept going until the cover it was on had finished, up to 10 minutes for a huge animated one, and could start again on its own. Stop now ends it straight away, a half-downloaded cover is thrown away so the next run fetches it again, and it doesn't restart after a sync until you start it again.
