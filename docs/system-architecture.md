# System architecture

Native Kotlin Android app for the Honor Magic7 Pro (MagicOS China, Android 17) that runs shell commands on the phone itself through its own Wireless debugging, without a PC or root. Jetpack Compose with Material 3 (dynamic color), Vietnamese UI.

## Modules

| Module | Role |
| --- | --- |
| `:app` | Compose UI (`ui/home`), `MainActivity`, release signing config, `WRITE_SECURE_SETTINGS` declaration |
| `:core:adb` | ADB client over Wireless debugging, pairing service, diagnostic log. No UI |
| `:core:ui` | Shared motion helpers: wavy progress, scroll motion blur, cookie shape, motion guard |
| `:feature:debloat` | Package scan, disable/enable through `AdbShell`, denylist, journal, Compose screen |

`:feature:circle-search` is added in its own phase.

## Debloat (`:feature:debloat`)

- Disables with `pm disable-user --user 0 <pkg>` and re-enables with `pm enable --user 0 <pkg>`; never uninstalls.
- `DebloatDenylist` blocks core packages by name/prefix; packages holding the HOME, SMS, DIALER, ASSISTANT or BROWSER role, the default IME and this app are resolved on the device and blocked as "in use". `DebloatController.disable` re-checks this on every call, so the UI cannot override it.
- Each successful change is appended to `files/debloat-journal.tsv`. "Xuất nhật ký" writes a text file (Storage Access Framework) with `adb shell pm enable --user 0 <pkg>` lines for everything this app still holds disabled.
- Packages not in `verifiedOnMagic7Pro` are shown as "Chưa đánh giá".

## Self ADB flow (`:core:adb`)

1. `AdbSession.connect()` finds the device's `_adb-tls-connect._tcp` service (libadb-android `connectTls`) and connects with the key kept in `filesDir` (`adb-key.pk8`, `adb-cert.der`). While `WRITE_SECURE_SETTINGS` is not yet granted, it runs `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` through the same connection.
2. No stored pairing: `connectTls` throws `AdbPairingRequiredException`, state becomes `NeedsPairing`.
3. Pairing: `PairingForegroundService` (foreground, `specialUse`) discovers `_adb-tls-pairing._tcp` through `NsdManager` while the user keeps "Pair with code" open in Developer options. It posts a notification with a `RemoteInput` for the 6-digit code. The same port is exposed as `AdbSession.pairingPort` so the UI can show an in-app code field when notifications are blocked.
4. `AdbSession.pairAndConnect(port, code)` pairs against `127.0.0.1:<port>` and then connects.
5. `AdbSession.run(command)` executes `shell:(<command>); echo <marker>$?` and returns `ShellResult(exitCode, output)`. It reconnects on demand and reports `Disconnected` when the stream fails.
   - The reader stops at the exit marker. Waiting for end-of-stream is not safe: on Android 15 (Samsung SM-S721B) adbd intermittently never closes the stream after a short command, which blocked `readText()` indefinitely.
   - libadb's read ignores interruption and disconnect, so each command runs on its own daemon thread with a 30 s deadline; on timeout the thread is abandoned (it stays blocked), the connection is dropped, the state becomes `Disconnected` and the received length is written to `DiagnosticLog`.
   - `connect`, `pairAndConnect` and `run` are serialized by one mutex, so there is a single connection and one command at a time. Commands are sent as `shell:<command>` + newline + `echo <marker>$?`.

Public surface: `AdbSession`, `AdbState`, `AdbShell`, `ShellResult`, `PairingForegroundService`, `DiagnosticLog`. Everything else is `internal`.

## Magic Capsule (Honor's dynamic island)

Not used by the pairing notification. Verified on the Magic7 Pro: an ongoing notification that sets the extra `android.requestPromotedOngoing` (and the manifest permission `POST_PROMOTED_NOTIFICATIONS`, granted automatically) is promoted by the framework (`PROMOTED_ONGOING`) and shows in Magic Capsule with the app icon and an elapsed-time counter. Tapping the capsule expands it with title, text and the notification actions, but a `RemoteInput` action gets no text field there: the reply button fires its `PendingIntent` immediately with no text, so the 6-digit code cannot be typed in the capsule. Use it for progress or status only, with a title and no custom `RemoteViews`.

The typed setter `Notification.Builder.setRequestPromotedOngoing` exists only from SDK 37, so on SDK 36 set the extra key directly. Honor also asks the user per app ("Cho phép thông báo dạng nang"), which the app cannot grant itself.

## MagicOS behaviour the design depends on (verified on Magic7 Pro)

- Pairing is lost after every reboot and the connect port changes; the app must send the user through pairing again. Wireless debugging itself stays enabled.
- A new app has notifications blocked by default (`importance=NONE`, appop uid mode `ignore`); the app requests `POST_NOTIFICATIONS` at runtime and keeps the in-app code field as fallback.
- The pairing dialog stays open while the notification shade is pulled down, which makes the notification code entry work.
- Third-party `Log.i/d` are filtered from logcat, so failures also go to `files/diagnostics.log` (`DiagnosticLog`).
- A `BOOT_COMPLETED` receiver did not run in the spike; not usable until the app is allowed to auto-launch (open item in phase 3).

Details and evidence: `plans/251025-1800-honor-toolkit.md`.

## Build

`./gradlew assembleDebug` / `assembleRelease`. Gradle 9.1.0, AGP 9.0.1 (built-in Kotlin 2.2.10), compileSdk 36, minSdk 30, ABI `arm64-v8a`, Compose BOM 2026.05.01. Newer Compose/lifecycle releases require AGP 9.1 and compileSdk 37, so they are not used yet. Signing: see `docs/deployment-guide.md`.
