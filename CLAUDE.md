# Siansa

Personal Android music player for Navidrome (Kotlin, Jetpack Compose, Media3, Coil 3, Room).
Source: `app/src/main/java/app/siansa/`. Unit tests: `app/src/test/`. Emulator tests: `app/src/androidTest/`.

## Every change

- Build and test before opening a pull request: `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:bundleRelease`.
  Lint errors fail the build.
- Add or update unit tests in `app/src/test/` for any logic you change.
- Add a note file in `changelog.d/` (e.g. `changelog.d/issue-42.md`, see its README) with `### Fixed` /
  `### Added` / `### Changed`, written for the app's user in plain words. Don't edit `CHANGELOG.md` or
  `version.txt`: the release does that, so pull requests never conflict over them. Use `### Added` only for
  something new to the user: it makes the release a new feature version (0.1.x → 0.2.0).
- Update the user docs for anything a user would notice (every `### Added` or `### Changed` note, a new or renamed
  setting, a changed default, a new requirement or a new service the app talks to). They live in
  `docs/` of this repository (see `docs/MAINTAINING.md` for which page). Change them in the same pull request, and say
  which pages in it. Later published at docs.siansa.app.
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
- Screenshot tests (`app/src/test/java/app/siansa/screenshots/ScreenshotTest.kt`, issue #710): draw a changed or new
  part of a page in a scene there, with long words where text can be cut off. When the look is meant to change, label
  the pull request `record screenshots` (CI records the pictures on GitHub's runner and commits them; pictures made on
  another machine can differ by a pixel), then look at the committed pictures in light and dark before asking for review.
- Navigate and change screen state only on the main thread.

## Automatic issues

Issues labelled `claude` are picked up by an hourly scheduled task in the owner's Claude workspace, which
implements them and opens a pull request. Labels show where each one is: `claude: working`, `claude: done`
(pull request open), `claude: needs info` (a question is in the issue's comments).

## Pull requests

- One issue per pull request; reference it with `Fixes #N`.
- Reports from the app (Settings › Help › Report a problem) are issues in this repository, with the log kept in the
  private logs repository. GitHub closes a fixed issue on merge, and the release comments on it with the version that
  has the fix (`scripts/notify_fixed.py`, issue #433).
- Describe before and after, and how it was tested.
- Don't merge: the owner reviews, and CI (`.github/workflows/ci.yml`) must pass. Pull requests run the build,
  Lint, unit tests and secret scan; emulator tests run after merging to main.

## Don'ts

- Don't upgrade Android Gradle Plugin, Compose, androidx.core, navigation, Coil or Media3 (see issue #23 and
  `.github/dependabot.yml`).
- The siansa.app server kit (Caddy, the sign-up and report service, emails, F-Droid repo scripts, website pages) and the
  owner's own runbooks are in the private `echovaultstudio/siansa-dev` repository, not here (#634, #636). The licences page stays here as
  `docs/licenses.html`; copy it to siansa-dev's `deploy/site/` when it changes.
- Don't commit secrets: the repository is public. The signing key lives only in CI's secrets (`SIGNING_KEYSTORE_BASE64`); a local build is unsigned unless `SIANSA_KEYSTORE` points at a key of your own.
