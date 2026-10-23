# Siansa

Personal Android music player for Navidrome (Kotlin, Jetpack Compose, Media3, Coil 3, Room).
Source: `app/src/main/java/app/siansa/`. Unit tests: `app/src/test/`. Emulator tests: `app/src/androidTest/`.

## Every change

- Build and test before opening a pull request: `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`.
  Lint errors fail the build.
- Add or update unit tests in `app/src/test/` for any logic you change.
- Add a note file in `changelog.d/` (e.g. `changelog.d/issue-42.md`, see its README) with `### Fixed` /
  `### Added` / `### Changed`, written for the app's user in plain words. Don't edit `CHANGELOG.md` or
  `version.txt`: the release does that, so pull requests never conflict over them. Use `### Added` only for
  something new to the user: it makes the release a new feature version (0.1.x → 0.2.0).
- Log what matters for tracing problems with `AppLog` (`i`, `w`, `e`; `d` for detail). Never log passwords,
  tokens or API keys (`AppLog.redact` removes them from addresses). Server addresses and IPs are logged: the log
  is a debugging tool and every request is written in full while it's on (issue #180).
- UI text: short, plain English, no jargon. Follow the existing Siansa theme (`ui/Theme.kt`); test light and dark.
- Spacing: every gap and padding is a step of the scale in `ui/Spacing.kt` (`Space.Tight` 4, `Small` 8,
  `Medium` 12, `Page` 16, `Section` 24, `Large` 32 dp), picked by what the space is for (see its comment).
  No raw dp numbers in `padding`, `spacedBy`, `PaddingValues` or `Spacer`: `SpacingPolicyTest` fails on them.
  Pixel-exact drawing that isn't spacing (a ring, a hairline, an offset) may say `// spacing: exact` on its line.
  Pages use `Space.Page` at their left and right edges and `Space.Section` between sections.
- Visual changes: keep spacing even (the same gap above and below a row of chips, buttons or a badge) and add
  a check for it to `app/src/androidTest/java/app/siansa/SpacingTest.kt`.
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
