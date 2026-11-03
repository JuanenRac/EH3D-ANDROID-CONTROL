# Building the release APK

## Building in GitHub Actions

The release APK is compiled by GitHub Actions, not on the maintainer's computer: start **Actions > APK build (manual)** (`.github/workflows/apk-build.yml`, `workflow_dispatch` only, never on a push or a pull request). Only the owner of the repository can start it, and the commit must already be part of `main`.

- **Inputs:** `dry_run` (on by default: build and check only) and `ref` (`main` by default).
- **Raise the version before the build:** `eh3d.project.json`, `versionName` and `versionCode` of `app/build.gradle.kts` and a section of `CHANGELOG.md`. The `plan` job fails if the `versionCode` does not rise over the one of the newest earlier release tag, and the `build` job reads the APK with `aapt2` and fails if its application id, version name or version code are not the planned ones.
- **Build:** JDK 21, `./gradlew :app:assembleRelease` (shrunk with R8) and `zipalign`, done twice (the two results are compared and the answer is written in the manifest). Gradle runs with its cache disabled, so a release never reads or writes a cache. The job refuses to run when it finds a keystore or a signing setting.
- **Jobs:** `plan`, `build`, `collect` (`SHA256SUMS`, `build-manifest.json`, build-provenance attestation), `verify` (checks the artifact as a stranger would), `draft` (only when `dry_run` is off, in the `release` environment, which waits for the approval of the owner) and `notify` (the issue `build-failed`, opened, updated and closed by the workflow itself).
- **File:** `EH3D-ANDROID-CONTROL-release-unsigned.apk`, **not signed** and not installable. The signature (`apksigner` with the release key) is made on the maintainer's computer, which keeps the key; Actions never has it.
- **A build that never ends:** `build-watchdog.yml` runs every hour and opens an issue `build-stuck` (with the link and how to cancel it) when a run has been queued or running for more than `max_minutes` of `.github/build-watchdog.json` (120). It closes the issue when none is stuck; a scheduled workflow stops after 60 days without activity in the repository, so start it by hand once if it did.

The local build (`build.bat`, `build.sh`) stays for development and for the tests.
