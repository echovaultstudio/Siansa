# Changelog notes

Each pull request adds one file here, for example `changelog.d/issue-42.md`, instead of editing
`CHANGELOG.md` or `version.txt`:

```markdown
### Fixed
- **Covers flashed a placeholder in lists.** They now show straight away.
```

Use `### Fixed`, `### Added` or `### Changed`, written for the app's user in plain words. When the change is
merged to main, CI raises the version by the size of the change:

- **Fixes and small changes** raise the last number: 0.1.3 → 0.1.4.
- **New features** (any note with `### Added`) raise the middle number: 0.1.3 → 0.2.0. A note can also ask for
  this with a line `<!-- release: minor -->`, for a big rework that adds nothing new.
- **1.0** and later major versions only come when a note says `<!-- release: major -->`.

It then adds a new section to the top of `CHANGELOG.md` from every
note waiting here, publishes the release with it, and removes the notes. Pull requests never conflict
over the changelog or the version this way.
