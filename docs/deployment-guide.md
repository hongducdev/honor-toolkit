# Deployment guide

## Debug build for the Magic7 Pro

```text
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

MagicOS shows a package-scan screen for every `adb install`. Wait for it to finish and tap Done; an unattended install can fail with `INSTALL_FAILED_ABORTED: User rejected permissions`.

## Release signing

`assembleRelease` signs only when `keystore.properties` exists in the repository root; otherwise it produces `app-release-unsigned.apk`. The file and the key stay out of git (`.gitignore`).

Create the key once, outside the repository, and keep a backup. Losing it means users must uninstall to update.

```text
keytool -genkeypair -v -keystore <path-outside-repo>/honor-toolkit-release.jks -alias honor-toolkit -keyalg RSA -keysize 4096 -validity 10000
```

`keystore.properties` (root of the repository):

```text
storeFile=<path-to-keystore>
storePassword=<store password>
keyAlias=honor-toolkit
keyPassword=<key password>
```

`storeFile` is resolved from the repository root; an absolute path works.

## In-app updates

`:feature:update` keeps the app current through GitHub releases of `honorToolkit.githubRepo` (default `hongducdev/honor-toolkit`).

- On launch the Home screen asks `releases/latest` at most once every 6 hours (`UpdateViewModel.checkIfDue`); the update page has a manual refresh and a switch for the automatic check.
- A release qualifies when its tag (`v0.2.0`) parses as a version newer than the installed `versionName` and it carries an `.apk` asset. Assets named `debug` or `unsigned` are skipped.
- The APK is downloaded to `cacheDir/updates`, checked against the SHA-256 digest from the GitHub API, and its package name is read with `getPackageArchiveInfo` before the system installer is launched through `FileProvider` (`<applicationId>.updates`).
- The first install needs "Install unknown apps" (`REQUEST_INSTALL_PACKAGES`). The page opens that setting and continues when the user returns; MagicOS then shows its package-scan screen.
- The installed version and the tag must match: the release workflow refuses a tag that differs from `honorToolkit.versionName`, otherwise the app would keep offering the same release.

## Releasing from GitHub

`.github/workflows/release.yml` runs on a `v*` tag (or manually) and publishes the signed APK plus a `.sha256` file. Required secrets are `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`; `.github/workflows/ci.yml` runs the unit tests and the debug build on every push and pull request.

To cut a release, bump `honorToolkit.versionCode` and `honorToolkit.versionName` in `gradle.properties`, push `main`, then push the matching tag. Do not re-use or move a tag: Android installs only when the APK's `versionCode` is higher than the installed one.

## Not yet verified

The minified release build compiles (R8 on, about 6 MB unsigned) and its `core.adb` and libadb classes are present, but it has not been installed and run on a device, because no release key exists yet.

## Testing on a second device

Any Android 11+ phone on the same Wi-Fi can be used for the self ADB flow. On a USB-attached phone, `uiautomator dump` exposes the pairing code in the "Pair with code" dialog and `adb shell input` drives the notification reply, so the pairing steps can be scripted. Samsung's setting is called "Gỡ lỗi qua Wi-Fi" and asks to allow the network.
