# System architecture

Native Kotlin Android app for the Honor Magic7 Pro (MagicOS China, Android 17) that runs shell commands on the phone itself through its own Wireless debugging, without a PC or root. Jetpack Compose with Material 3 (dynamic color), Vietnamese UI.

## Modules

| Module | Role |
| --- | --- |
| `:app` | Compose UI (`ui/home`), `MainActivity`, release signing config, `WRITE_SECURE_SETTINGS` declaration |
| `:core:adb` | ADB client over Wireless debugging, pairing service, diagnostic log. No UI |
| `:core:quickshare` | Quick Share / Nearby Share protocol engine for phones without GMS. Pure JVM (no `android.*`), protobuf-javalite, JVM unit tests. No UI, no discovery yet |
| `:core:ui` | Shared motion helpers: wavy progress, scroll motion blur, cookie shape, motion guard |
| `:feature:debloat` | Package scan, disable/enable through `AdbShell`, denylist, journal, Compose screen |
| `:feature:permissions` | Runtime permission grant/revoke and AppOps for third-party apps through `AdbShell`, guard, read-back verification, journal, Compose screen |
| `:feature:gms-check` | Read-only check of Google Play Services, Services Framework, Play Store (installed, enabled, version) and the number of Google accounts, through `AdbShell` |
| `:feature:tune` | Fixed MagicOS settings catalogue (performance, system notifications, China-UI cleanup, network/GPS), every write read back, presets and restore, Compose screen |
| `:feature:circle-search` | Circle to Search trigger (hidden voice-interaction API), Quick Settings tiles, nav-bar strip, offline Vosk wake word ("OK Google"), assistant setup, Compose screen |
| `:feature:update` | GitHub release check, APK download with SHA-256 check, system installer hand-off, Compose update page and Home banner |

## Quick Share engine (`:core:quickshare`)

Written from NearDrop's `PROTOCOL.md` and Swift sources (Unlicense) and the protos in `src/main/proto` (Google, Apache-2.0). Bada has no licence, so none of its code is used; its interop notes were used only as a checklist of observed peer behaviour.

- The receiver listens on a TCP port and advertises mDNS type `_FC9F5ED42C8A._tcp` with a base64url name from `EndpointNaming.mdnsServiceName` and a TXT `n` record from `EndpointNaming.endpointInfoRecord`. The sender connects. Discovery and the foreground service are not part of this module.
- Flow: plain `ConnectionRequest` → UKEY2 (`Ukey2Client` / `Ukey2Server`, P256_SHA512) → plain `ConnectionResponse` → everything else through `SecureChannel` (AES-256-CBC + HMAC-SHA256, MAC checked before decryption, sequence numbers checked). Sharing frames (paired key, introduction, response) travel as BYTES payloads sent as a data chunk plus an empty LAST_CHUNK.
- `InboundConnection` / `OutboundConnection` are blocking: `run()` returns a `TransferOutcome`, `cancel()` stops it from another thread. Consent is asynchronous: `InboundListener.onOffer` receives a `ConsentResponder` to answer later from any thread. Keep-alives go out every 10 s; the read timeout is 30 s.
- Behaviour that Samsung One UI needs and is implemented: OS type ANDROID, `multiplex_socket_bitmask = 0`, `safe_to_disconnect_version = 1` in the connection response; `use_case = NEARBY_SHARE` and `FileMetadata.id = payload_id` in the introduction; an empty LAST_CHUNK frame after each file; a Disconnection with `request_safe_to_disconnect` followed by up to 1.5 s of waiting for the peer.
- Open point: the ECDH x-coordinate is hashed as its minimal magnitude (NearDrop). If GMS hashes the fixed 32-byte form, roughly 1 handshake in 256 fails; check on a device.
- Receiving is wired in `:feature:quickshare` (service, mDNS advertise, MediaStore sink, notification consent with PIN). Verified on a Samsung SM-S721B only that the foreground service starts and mDNS registers; no transfer from a real Quick Share sender has been tested yet.
- Sending: `QuickShareSendActivity` (share-sheet target in `:app`) browses mDNS through `MdnsBrowser`, shows the PIN and streams the files. Verified: Honor Magic7 Pro to the stock Samsung SM-S721B Quick Share, 318134-byte JPEG received with the same size, PINs matched. A stock receiver in contacts mode sets the hidden bit and publishes no name, so it is listed as "Thiết bị Quick Share" with its IP.
- Not done: BLE (service UUIDs 0xFE2C / 0xFEF3), Wi-Fi Direct upgrade, share-sheet send activity. Tracked in `plans/261026-1200-quick-share-from-bada.md`.

## Circle to Search (`:feature:circle-search`)

- `CircleSearchLauncher` opens an assist session of the voice interaction service through `HiddenApiBypass`; no ADB needed. The module ships a consumer proguard rule keeping the bypass library. Circle uses the screenshot entry point (`omni.entry_point=1`, flags 7); Gemini uses `invocation_type=7` with `request_assist_screenshot` and falls back to `ACTION_VOICE_COMMAND` when the session is refused.
- A refused first attempt is retried once after `GoogleWarmUp` touches Google's providers, binds its SSB service and sends its wake-up broadcast, because a frozen Google process rejects sessions. The warm-up is also prewarmed in the background at most every 5 s.
- `TriggerActivity` is exported but is not a launcher activity (the app keeps one icon). It is reached through a static shortcut on `MainActivity`, the Quick Settings tile, or key assignment; it only runs the fixed action.
- `AssistantSetup` (needs ADB) records the current ASSISTANT role holder, adds Google as holder, then sets the `assistant` and `voice_interaction_service` secure settings if the role alone did not make the setting match. `restore` re-adds the recorded holder.
- The strip (`NavBarStripService`, foreground `specialUse`, notification id 2) is opt-in and off by default; `BootReceiver` is not exported. It hides itself for 90 ms before an assist launch so it does not appear in the screenshot Circle to Search reads, re-adds its window from a 30 s health check, and offers "Tắt dải" in its quiet notification. It maps tap, double tap and hold to `none`/`circle`/`gemini`/`assistant` (defaults: tap none, double Gemini, hold Circle); swipes are not handled so the system navigation gestures keep working.
- `TriggerLog` keeps the last 20 attempts (source, detail, the assistant that received the request, OK/FAIL) in `SharedPreferences` and is shown on the trigger page with copy/clear, because MagicOS hides third-party logcat lines.
- Wake word ("Đánh thức bằng giọng nói"): opt-in, off by default. `WakeWordService` is a foreground `microphone` service that runs Vosk/Kaldi fully on device. The pipeline is ported from Trợ lý VXM (`WakeAudio`, `WakeFilter`, `WakeLevel`, `SpeechGate`, `PlaybackGate`): 16 kHz mono PCM16, 1280-sample buffers, `okay google` as the only accepted phrase, a distractor-rich grammar (VXM's filler words plus `hey google` and `[unk]`), an 80 ms stability window counted over decoded samples, a loudness gate, a 1 s media-playback gate and a 5 s cooldown between wakes. A final result is only accepted when a matching partial preceded it; a bare final is how the decoder turns room noise into a wake phrase at end of utterance. The 70 MB US English mobile model ships in `assets/model-en` and is unpacked to external files on first use. On a verified wake the service releases the microphone, runs the same Gemini session as the strip (`TriggerRunner.runGemini`, source `OK Google`), then polls `AudioManager.getActiveRecordingConfigurations()` and listens again once Gemini has stopped recording. The page shows the live status, last heard phrase/time, microphone and notification permissions, a battery-optimisation shortcut and a "Thử mở Gemini" button that simulates a wake. `BootReceiver` tries to restart it after boot; on Android 14+ a microphone foreground service may be refused there, so the screen remains the reliable switch.
- The wake module vendors Vosk/Kaldi (Apache-2.0) and the Alpha Cephei US English mobile model; the model's own README and copyright are kept in `assets/model-en/README`.

## GMS check (`:feature:gms-check`)

- `GmsCheckController.check()` runs `pm list packages`, `pm list packages -d`, `dumpsys package <pkg>` (version) and `dumpsys account` (counts `type=com.google` accounts; addresses are never kept). It changes nothing.
- Parsers are covered by unit tests on real `dumpsys` output. Push-notification (FCM) health is not checked: no verified read-only command yet.

## Notification fix: standby bucket

`FixStep.StandbyBucket` reads `am get-standby-bucket <pkg>` (satisfied at 20 or lower) and applies `am set-standby-bucket <pkg> active`. Like the other steps it is re-read afterwards, so a ROM that ignores it shows as `Failed`. Not yet verified on MagicOS.

## MagicOS tune (`:feature:tune`)

- `TuneCatalog` is a fixed list of 17 toggles and two choices. Each toggle names the settings it writes and the exact values that count as on and off; a choice maps its options to commands and reads one key back. No shell string is built from user input.
- Reads are batched: one shell command prints `__HT_<namespace>/<key>=` before each `settings get`, and `parseSettingsDump` splits on the first `=` so values that contain `=` (the JobScheduler constant) survive.
- `setToggle` and `choose` write, then read the same keys again and return `Applied` only when the ROM kept the value; `Refused` means the phone ignored it. `applyRecommended` and `restoreDefaults` count how many rows match afterwards.
- The catalogue covers what the China-ROM notification problem actually needs and what MagicTune 2.6 wires on the same firmware: gentle/cooldown switches, heads-up and lock-screen detail, JobScheduler batching, HiBoard/Honor Search/magazine/YOYO wake, Honor Market auto-update, ad personalisation, captive-portal check, `min_match`, VoLTE/5G, Google SUPL and AdGuard Private DNS.
- Package disabling stays in `:feature:debloat`; the tune page only writes settings. "Khôi phục mặc định" deletes every setting key the page can write so the ROM falls back to its compiled defaults, then counts how many keys are really gone; it does not guess a stock value per row.
- Not verified on a device. A row whose keys this ROM does not expose is shown as "Không đọc được trạng thái" instead of being guessed as off.

## Debloat (`:feature:debloat`)

- Disables with `pm disable-user --user 0 <pkg>` and re-enables with `pm enable --user 0 <pkg>`; never uninstalls.
- `DebloatDenylist` blocks core packages by name/prefix; packages holding the HOME, SMS, DIALER, ASSISTANT or BROWSER role, the default IME and this app are resolved on the device and blocked as "in use". `DebloatController.disable` re-checks this on every call, so the UI cannot override it.
- Each successful change is appended to `files/debloat-journal.tsv`. "Xuất nhật ký" writes a text file (Storage Access Framework) with `adb shell pm enable --user 0 <pkg>` lines for everything this app still holds disabled.
- "Khôi phục tất cả" (toolbar action) re-enables, one at a time, the packages the journal still holds disabled that are also currently disabled on the device (`restorePackages`); it stops at the first failure and reports how many were restored.
- Packages not in `verifiedOnMagic7Pro` are shown as "Chưa đánh giá".
- `DebloatSuggestions` is an advisory list of Honor/Huawei China packages with a short Vietnamese reason and a "Nên tắt" / "Cân nhắc" level, shown under the package name and reachable through the "Gợi ý dọn" filter. It does not make a package eligible: the denylist and in-use guard still run on every disable, and nothing in the list is claimed as verified on the Magic7 Pro.

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

## Quick Share offers on MagicOS (verified on Magic7 Pro)

- The offer notification is ongoing, carries `android.requestPromotedOngoing` and a chronometer; with the app in the background it shows in Magic Capsule, and tapping the capsule shows the Nhận / Từ chối actions. The framework promotes it only after `cmd appops set <pkg> POST_PROMOTED_NOTIFICATIONS allow` (the screen runs it when ADB is connected).
- Magic Capsule is not shown for the foreground app, and MagicOS lowers the new "offer" channel to importance 3 (no banner) whatever the app requests. `QuickShareOfferDialog` therefore shows a dialog on every screen while an offer is pending (`ReceiverStatus.offer`).
- Answering an offer writes to the peer socket, so it runs on the service executor, never on the main thread.

## MagicOS behaviour the design depends on (verified on Magic7 Pro)

- Pairing is lost after every reboot and the connect port changes; the app must send the user through pairing again. Wireless debugging itself stays enabled.
- A new app has notifications blocked by default (`importance=NONE`, appop uid mode `ignore`); the app requests `POST_NOTIFICATIONS` at runtime and keeps the in-app code field as fallback.
- The pairing dialog stays open while the notification shade is pulled down, which makes the notification code entry work.
- Third-party `Log.i/d` are filtered from logcat, so failures also go to `files/diagnostics.log` (`DiagnosticLog`).
- A `BOOT_COMPLETED` receiver did not run in the spike; not usable until the app is allowed to auto-launch (open item in phase 3).

Details and evidence: `plans/251025-1800-honor-toolkit.md`.

## Build

`./gradlew assembleDebug` / `assembleRelease`. Gradle 9.1.0, AGP 9.0.1 (built-in Kotlin 2.2.10), compileSdk 36, minSdk 30, ABI `arm64-v8a`, Compose BOM 2026.05.01. Newer Compose/lifecycle releases require AGP 9.1 and compileSdk 37, so they are not used yet. Signing: see `docs/deployment-guide.md`.
