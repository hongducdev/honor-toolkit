# Honor Toolkit

[![CI](https://github.com/hongducdev/honor-toolkit/actions/workflows/ci.yml/badge.svg)](https://github.com/hongducdev/honor-toolkit/actions/workflows/ci.yml)

A native Android app for Honor phones on MagicOS (built and tested against the Magic7 Pro, China ROM, Android 17). It talks to a phone's own Wireless debugging over ADB, so system-level tools work without a PC and without root. The UI is Vietnamese, Jetpack Compose with Material 3 dynamic colour.

## Install

Download the latest signed APK from [Releases](https://github.com/hongducdev/honor-toolkit/releases/latest) and open it, or install over ADB:

```text
adb install -r honor-toolkit-v<version>.apk
```

After the first install the app updates itself from those same releases.

## Features

| Area | What it does |
| --- | --- |
| Self ADB | Pairs with the phone's own wireless debugging, keeps the RSA key, runs shell commands through the app |
| Debloat | Disables system packages with a denylist, role and default-IME guard, curated China-app hints, journal and one-tap restore |
| Permissions | Grants and revokes runtime permissions and AppOps for third-party apps, reads every change back |
| Notification fix | Applies notification and standby-bucket fixes through ADB, reports each step |
| MagicOS tune | Refresh rate, animation scale, system-wide notification switches, China-UI cleanup, GPS/SUPL and VoLTE/5G, each write read back |
| Automation | One-condition rules with an ordered action list: charger, battery, screen, Wi-Fi, Bluetooth, clock, app, notification and proximity triggers; settings, ringer, app, notification, wait, ADB shell, Toolkit feature and Circle to Search actions; journal and undo, runs while locked and after reboot |
| GMS check | Read-only check of Play Services, Services Framework, Play Store and Google accounts |
| Circle to Search | Assist-session trigger, Quick Settings tile, nav-bar strip, offline "OK Google" wake word |
| Quick Share | Receives from Samsung and Pixel over Wi-Fi, and sends from the share sheet |
| In-app update | Reads the latest GitHub release, downloads the APK, verifies it and hands it to the Android installer |

## Build

Requirements: JDK 17, Android SDK 36, `local.properties` with `sdk.dir`.

```text
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release signing, device notes and the update flow: [docs/deployment-guide.md](docs/deployment-guide.md). Module map: [docs/system-architecture.md](docs/system-architecture.md).

## In-app updates

`:feature:update` calls `https://api.github.com/repos/hongducdev/honor-toolkit/releases/latest` on launch (at most once every 6 hours) and from the update page. When the release tag is newer than the installed version it shows a banner on Home and a page with release notes.

- The release must carry an `.apk` asset; a name containing `honor-toolkit` is preferred and `debug`/`unsigned` assets are skipped.
- The download is checked against the SHA-256 digest GitHub reports for the asset, then against the expected package name before the system installer is opened.
- The first install asks for "Install unknown apps"; MagicOS then shows its package-scan screen.
- The repository is set by `honorToolkit.githubRepo` in `gradle.properties` and compiled into `BuildConfig.GITHUB_REPO`.

## Release a new version

1. Bump `honorToolkit.versionCode` (must increase) and `honorToolkit.versionName` in `gradle.properties`.
2. Commit and push to `main`.
3. Tag and push: `git tag v<versionName>` then `git push origin v<versionName>`.

`.github/workflows/release.yml` builds the signed APK, writes a SHA-256 file and publishes the GitHub release with generated notes. It fails if the tag does not match `honorToolkit.versionName`.

The workflow needs four repository secrets:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | `base64` of the release `.jks` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias, `honor-toolkit` |
| `SIGNING_KEY_PASSWORD` | key password |

Keep the keystore file and its passwords backed up outside the repository. Losing the key means users have to uninstall before they can install a newer build.
