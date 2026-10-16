# Musix

Personal Android music player for Navidrome (Kotlin, Jetpack Compose, Media3, Coil 3, Room).
Source: `app/src/main/java/app/musix/`. Unit tests: `app/src/test/`. Emulator tests: `app/src/androidTest/`.

## Every change

- Build and test before opening a pull request: `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`.
  Lint errors fail the build.
- Add or update unit tests in `app/src/test/` for any logic you change.
- Add a section at the top of `CHANGELOG.md` and set the same version in `version.txt` (next `0.0.Nbeta`).
  Write it for the app's user: what was wrong or new, in plain words, under `### Fixed` / `### Added` / `### Changed`.
- Log what matters for tracing problems with `AppLog` (`i`, `w`, `e`; `d` for detail). Never log addresses,
  passwords, tokens or API keys.
- UI text: short, plain English, no jargon. Follow the existing Musix theme (`ui/Theme.kt`); test light and dark.
- Navigate and change screen state only on the main thread.

## Pull requests

- One issue per pull request; reference it with `Fixes #N`.
- Describe before and after, and how it was tested.
- Don't merge: the owner reviews, and CI (`.github/workflows/ci.yml`) must pass.

## Don'ts

- Don't upgrade Android Gradle Plugin, Compose, androidx.core, navigation, Coil or Media3 (see issue #23 and
  `.github/dependabot.yml`).
- Don't commit secrets. The signing keystore in `app/` is intentional while the repository is private.
