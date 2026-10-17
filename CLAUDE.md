# Musix

Personal Android music player for Navidrome (Kotlin, Jetpack Compose, Media3, Coil 3, Room).
Source: `app/src/main/java/app/musix/`. Unit tests: `app/src/test/`. Emulator tests: `app/src/androidTest/`.

## Every change

- Build and test before opening a pull request: `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`.
  Lint errors fail the build.
- Add or update unit tests in `app/src/test/` for any logic you change.
- Add a note file in `changelog.d/` (e.g. `changelog.d/issue-42.md`, see its README) with `### Fixed` /
  `### Added` / `### Changed`, written for the app's user in plain words. Don't edit `CHANGELOG.md` or
  `version.txt`: the release does that, so pull requests never conflict over them. Use `### Added` only for
  something new to the user: it makes the release a new feature version (0.1.x → 0.2.0).
- Log what matters for tracing problems with `AppLog` (`i`, `w`, `e`; `d` for detail). Never log addresses,
  passwords, tokens or API keys.
- UI text: short, plain English, no jargon. Follow the existing Musix theme (`ui/Theme.kt`); test light and dark.
- Visual changes: keep spacing even (the same gap above and below a row of chips, buttons or a badge) and add
  a check for it to `app/src/androidTest/java/app/musix/SpacingTest.kt`.
- Navigate and change screen state only on the main thread.

## Automatic issues

Issues labelled `claude` are picked up by an hourly scheduled task in the owner's Claude workspace, which
implements them and opens a pull request. Labels show where each one is: `claude: working`, `claude: done`
(pull request open), `claude: needs info` (a question is in the issue's comments).

## Pull requests

- One issue per pull request; reference it with `Fixes #N`.
- Describe before and after, and how it was tested.
- Don't merge: the owner reviews, and CI (`.github/workflows/ci.yml`) must pass. Pull requests run the build,
  Lint, unit tests and secret scan; emulator tests run after merging to main.

## Don'ts

- Don't upgrade Android Gradle Plugin, Compose, androidx.core, navigation, Coil or Media3 (see issue #23 and
  `.github/dependabot.yml`).
- Don't commit secrets. The signing keystore in `app/` is intentional while the repository is private.
