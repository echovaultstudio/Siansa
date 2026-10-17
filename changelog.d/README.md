# Changelog notes

Each pull request adds one file here, for example `changelog.d/issue-42.md`, instead of editing
`CHANGELOG.md` or `version.txt`:

```markdown
### Fixed
- **Covers flashed a placeholder in lists.** They now show straight away.
```

Use `### Fixed`, `### Added` or `### Changed`, written for the app's user in plain words. When the change is
merged to main, CI raises the version by one, adds a new section to the top of `CHANGELOG.md` from every
note waiting here, publishes the release with it, and removes the notes. Pull requests never conflict
over the changelog or the version this way.
