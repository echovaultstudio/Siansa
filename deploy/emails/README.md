# Emails sent to beta testers

Edit these files, merge the change, and the next email uses the new text (no restart). Each file starts with
`Subject: …`, then a blank line, then the message. Placeholders (written `$name`):

| Placeholder | What it becomes |
|---|---|
| `$repo_url` | The private F-Droid beta address (F-Droid emails only) |
| `$beta_repo_url` | The install guide on GitHub (`docs/testing.md`) |
| `$issues_url` | The page for reporting a problem |
| `$email` | The tester's email address |
| `$tail` | The shared closing in `tail.txt` |

Write `$$` for a real dollar sign. A file that is missing or broken falls back to the built-in text, so a typo
can't stop a welcome email from going out.
