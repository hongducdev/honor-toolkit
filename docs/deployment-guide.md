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

## Not yet verified

The minified release build compiles (R8 on, about 6 MB unsigned) and its `core.adb` and libadb classes are present, but it has not been installed and run on a device, because no release key exists yet.

## Testing on a second device

Any Android 11+ phone on the same Wi-Fi can be used for the self ADB flow. On a USB-attached phone, `uiautomator dump` exposes the pairing code in the "Pair with code" dialog and `adb shell input` drives the notification reply, so the pairing steps can be scripted. Samsung's setting is called "Gỡ lỗi qua Wi-Fi" and asks to allow the network.
