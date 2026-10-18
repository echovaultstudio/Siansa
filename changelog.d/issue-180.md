### Changed
- **The log now records every request in full** while it's on: which server and IP address it went to, the whole address with its settings, the headers, what came back, its size and how long it took. Covers and song streams are included too. Passwords, tokens and API keys are still removed. Logs grow faster, so each part now holds 15 MB.
