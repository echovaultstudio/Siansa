# Making the repository public

The code is ready for a public repository once #584 is merged: the signing key is gone from the tree, releases
carry no files, the licence is in. What's left is the **git history**, which still holds `app/siansa.keystore` and
its password in every commit since the rename (#360), and the retired `app/musix.keystore` before it. A scan of all 1,066 commits found no other keys, tokens or passwords: only made-up ones in
tests, home-network addresses (192.168.x.x, not reachable from outside) and the notify address in an old
`deploy/.env.example`, which the rewrite replaces too. Making the repository public with that history would give
everyone the key that signs the app. So the history is rewritten first, once, by the owner, on a machine with a
fresh clone. Nothing below runs in CI.

## 1. Before the rewrite

- Merge #584 (this), and any open pull requests you want to keep. **Every branch is rewritten**, so open pull
  requests survive, but anyone with a clone must clone again afterwards.
- Add the signing secrets to the repository (Settings › Secrets and variables › Actions):
  `SIGNING_KEYSTORE_BASE64` (`base64 -w0 app/siansa.keystore` from an old clone), `SIGNING_KEYSTORE_PASSWORD`,
  `SIGNING_KEY_ALIAS` (`siansa`), `SIGNING_KEY_PASSWORD`. Keep the keystore file itself in a password manager:
  after the rewrite, GitHub no longer has it.
- Run one release build on main with the secrets in place and check the Play upload and F-Droid publish still
  work (the APK is signed with the same key as before, so testers update as usual).

## 2. The rewrite (done 2026-10-28)

The history was rewritten once with [git-filter-repo](https://github.com/newren/git-filter-repo) on a fresh
`--mirror` clone: both keystores (`app/siansa.keystore`, and the retired `app/musix.keystore`) dropped from every
commit, the two passwords written in old build files and an old example address replaced, then every branch and tag
force-pushed. Tags were rewritten with their commits, so releases point at the right code. It was checked
beforehand: the keystore paths and both passwords find nothing in `git log --all`, the tag count was the same as
before, and the code on main matched the old main except for the replaced words. The passwords are deliberately not
written here or in the tests.

## 3. After the rewrite

- **The self-hosted runner's clone:** GitHub's runner re-clones on its own; nothing to do.
- **Every pull request branch** was rewritten with the rest, so open pull requests stay open. Anyone with a
  local clone (including Claude's sessions) must clone again; a push from an old clone would bring the key back.
- **GitHub keeps old commits reachable by hash for a while** (cached views, pull request pages). Ask GitHub
  Support to run garbage collection on the repository, or wait for it; until then don't rely on the old hashes
  being gone.
- **Make the repository public:** Settings › General › Danger zone › Change visibility. GitHub then warns about
  secrets it scans for; the keystore isn't one it recognises, which is why the rewrite comes first.
- **Turn on** Settings › Code security › Secret scanning and Push protection, so a key can't be pushed again.
- Delete the `claude/*` branches that are already merged (Settings › Branches, or `git push origin --delete`), so
  the public branch list is just main and open work.

## 4. Google Play: open testing later

A new Play developer account can't use open testing (or production) until a closed test has had at least 12
testers opted in for 14 days. Until then releases go to closed and internal testing, as before, and testers sign
up at siansa.app. Once Play Console allows it: set up Open testing (countries, feedback address), then add the
repository variable `PLAY_TRACK` = `beta` (or `beta,internal`) so releases go there.

## 5. Not covered by this

- `deploy/` holds the server kit (Caddy, the signup service, the F-Droid repo). It has no secrets (they're in
  `.env`, which is ignored), but it describes the server; keep it or move it to a private repository as you prefer.
- The beta testers' logs stay in the private `siansa-logs` repository; nothing links there from the public code
  except the report id in an issue.
