# Codebase summary

Entry points and where each concern lives. Architecture and rationale: `system-architecture.md`.

## `:app` (`app/src/main/kotlin/vn/hongducdev/honortoolkit`)

| File | Concern |
| --- | --- |
| `main-activity.kt` | Launcher activity, edge-to-edge, hosts the screens behind an M3 Expressive `HorizontalFloatingToolbar` (Home, Circle, Debloat and Permissions when ADB is connected, ADB) |
| `ui/theme/honor-toolkit-theme.kt` | Material 3 theme with dynamic color on Android 12+ |
| `ui/home/home-view-model.kt` | `HomeUiState`, combines `AdbSession` flows, reconnects on start |
| `core/ui/toolkit-components.kt` | Shared `ToolkitTopBar`, `SettingsGroup`/`SettingsRow`, `SwitchRow`, `SliderRow`, `NavRow` (haptics on interaction) |
| `ui/home/home-screen.kt` | Home: connection status + tool list (Circle to Search, Debloat, Permissions, ADB connection) |
| `ui/home/connection-screen.kt` | ADB connection screen: pairing flow, notification-permission request, shell probe card |
| `ui/home/connection-status-card.kt` | Connection state and reconnect button |
| `ui/home/pairing-card.kt` | Pairing steps, start button, in-app code field |

Strings are Vietnamese in `res/values/strings.xml`.

## `:core:adb` (`core/adb/src/main/kotlin/vn/hongducdev/honortoolkit/core/adb`)

| File | Concern |
| --- | --- |
| `adb-session.kt` | Connect, pair, run command, state and pairing-port flows |
| `adb-state.kt` | `AdbState`, `ShellResult`, `AdbShell` |
| `adb-key-store.kt` | Persistent RSA key and self-signed certificate (BouncyCastle) |
| `honor-adb-connection-manager.kt` | libadb-android subclass |
| `pairing-discovery.kt` | NSD discovery of the pairing port |
| `pairing-foreground-service.kt` | Foreground service and notification code entry |
| `diagnostic-log.kt` | File log readable with `run-as` |

## `:core:ui` (`core/ui/src/main/kotlin/vn/hongducdev/honortoolkit/core/ui`)

| File | Concern |
| --- | --- |
| `toolkit-motion.kt` | `rememberMotionEnabled`: off in battery saver or when animations are disabled |
| `scroll-motion-blur-modifier.kt` | AGSL vertical blur proportional to scroll speed (Android 13+, no-op below) |
| `wavy-progress-indicator.kt` | Animated sine-wave indeterminate progress |
| `cookie-shape.kt` | Scalloped badge shape from graphics-shapes |

## `:core:quickshare` (`core/quickshare/src/main/kotlin/vn/hongducdev/honortoolkit/core/quickshare`)

| File | Concern |
| --- | --- |
| `crypto/hkdf.kt`, `crypto/key-derivation.kt` | HKDF-SHA256, UKEY2 auth/next secrets, D2D and SecureMessage keys per role, 4-digit PIN |
| `crypto/p256-keys.kt` | P-256 key pairs, `GenericPublicKey` encoding, on-curve check, ECDH |
| `ukey2/ukey2-handshake.kt` | `Ukey2Server` and `Ukey2Client`, alert mapping |
| `transport/framed-connection.kt` | 4-byte length framing, 5 MB cap |
| `channel/secure-channel.kt` | Encrypt, sign, verify, decrypt, sequence numbers |
| `endpoint/endpoint-info.kt` | Endpoint info bytes, endpoint id, mDNS service name and TXT value |
| `connection/frame-builders.kt` | Offline and sharing frame constructors |
| `connection/nearby-connection.kt` | Shared read loop, keep-alive, bytes payload reassembly, shutdown |
| `connection/inbound-connection.kt`, `connection/outbound-connection.kt` | Receiver and sender state machines, public `InboundConnection` / `OutboundConnection` |
| `connection/transfer-types.kt` | Public listeners, offers, sinks, `TransferOutcome` |
| `src/main/proto/*.proto` | Wire formats copied from NearDrop (Google's, Apache-2.0) |

Tests in `core/quickshare/src/test`: RFC 5869 vectors, key and PIN checks, endpoint info, and loopback transfers between `OutboundConnection` and `InboundConnection` over real sockets.

## `:feature:quickshare` (`feature/quickshare/src/main/kotlin/vn/hongducdev/honortoolkit/feature/quickshare`)

| File | Concern |
| --- | --- |
| `quick-share-receiver-service.kt` | Foreground service (`connectedDevice`): `ServerSocket`, accept loop, one `InboundConnection` per peer, consent and progress notifications, 60 s offer timeout |
| `mdns-advertiser.kt` | `NsdManager` registration of `_FC9F5ED42C8A._tcp` with the TXT `n` record |
| `media-store-file-sink.kt` | `FileSink` writing to Download/QuickShare through MediaStore (pending until commit), sanitised names |
| `quick-share-offer-dialog.kt` | Consent dialog hosted in `MainActivity`, shown over any screen while an offer is pending |
| `quick-share-send-view-model.kt`, `quick-share-send-screen.kt`, `mdns-browser.kt`, `shared-stream-uris.kt` | Sending: browse receivers, connect with `OutboundConnection`, PIN, progress, cancel; `QuickShareSendActivity` in `:app` hosts it for the share sheet |
| `quick-share-state.kt` | `ReceiverStatus` flow shared by service and screen |
| `quick-share-screen.kt` | Sub-page opened from Home: receive switch, status, last result, notification permission request |

## `:feature:gms-check` (`feature/gms-check/src/main/kotlin/vn/hongducdev/honortoolkit/feature/gmscheck`)

| File | Concern |
| --- | --- |
| `gms-check-parsers.kt` | `pm list packages`, `dumpsys package` version and `dumpsys account` parsers |
| `gms-check-controller.kt` | `GmsCheckController.check()` builds a `GmsReport` through `AdbShell` |
| `gms-check-view-model.kt`, `gms-check-screen.kt` | State and Compose sub-page opened from the Apps tab |

## `:feature:tune` (`feature/tune/src/main/kotlin/vn/hongducdev/honortoolkit/feature/tune`)

| File | Concern |
| --- | --- |
| `tune-settings.kt` | `TuneCatalog`: the fixed toggles, choices, recommended set, GPS-refresh and restore commands |
| `tune-parsers.kt` | Batched `settings get` parser and refresh-rate parsing |
| `tune-controller.kt` | Snapshot, toggle/choice apply with read-back, recommended preset, restore, GPS refresh |
| `tune-view-model.kt` | Loading state, current values, messages, restore confirmation |
| `tune-screen.kt` | Compose sub-page opened from the Apps tab (switch rows, chips, GPS action, restore dialog) |

## `:feature:automation` (`feature/automation/src/main/kotlin/vn/hongducdev/honortoolkit/feature/automation`)

| File | Concern |
| --- | --- |
| `automation-model.kt` | `AutomationRule`, sealed `Trigger`/`Action`/`TriggerEvent`, matching, validation and Vietnamese summaries |
| `automation-json.kt` | Versioned `org.json` encoding; a broken rule or action is dropped without losing the list |
| `automation-store.kt` | Device-protected `SharedPreferences`: rules, 60-line journal, undo snapshots (`SettingSnapshot`) |
| `automation-engine.kt` | Sequential dispatch, running-rule and 2 s loop guards, `canRun` permission gate, per-step log |
| `automation-settings.kt` | `Settings.*` read/write with read-back, plus the allowlist/denylist policy |
| `automation-actions.kt` | Android executor: settings, ringer, launch app, notification, wait, ADB shell, Tune feature, nav-bar strip, Circle to Search; locked/unlocked skips |
| `automation-runtime.kt` | Process-wide engine; dispatches from services and receivers, supports `goAsync` completion |
| `automation-foreground-service.kt` | `specialUse` service, battery/power/screen/Bluetooth/clock receivers, Wi-Fi callback, quiet notification, `sync()` start/stop |
| `automation-alarms.kt`, `automation-location.kt`, `automation-schedule-store.kt` | Time alarms and proximity alerts per rule, rescheduling/cancelling, receiving and dispatch |
| `automation-boot-receiver.kt` | `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` restart |
| `automation-accessibility-service.kt` | App open/close via window-state changes, filtered by package in code |
| `automation-notification-listener.kt` | Notification trigger; text only in memory for keyword matching |
| `automation-permissions.kt` | Requirement set per trigger/action, grant checks, settings intents |
| `automation-view-model.kt` | Rule list/editor state, toggle/save/delete, undo, runtime sync |
| `automation-screen.kt`, `automation-editor-screen.kt` | Compose list (permission cards, rules, journal) and rule editor (trigger/action pickers) |
| `src/test` | JSON round-trip, engine ordering/queue/loop/failure/permission, settings policy, trigger matching and rule validation |

## `:feature:debloat` (`feature/debloat/src/main/kotlin/vn/hongducdev/honortoolkit/feature/debloat`)

| File | Concern |
| --- | --- |
| `debloat-denylist.kt` | Hard denylist (exact names, prefixes) plus runtime "in use" packages |
| `debloat-suggestions.kt` | Advisory list of China-ROM Honor/Huawei packages with a Vietnamese reason and confidence level |
| `package-scanner.kt` | `pm list packages -s [-d]`, labels, role holders and default IME lookup |
| `debloat-controller.kt` | `disable` / `enable` with name validation and denylist re-check |
| `debloat-journal.kt` | Append-only `files/debloat-journal.tsv`, export text with PC recovery commands |
| `debloat-restore.kt` | `restorePackages`: sequential bulk re-enable, stops at first failure |
| `package-name-validator.kt` | Rejects anything that is not a plain package name before it reaches a shell |
| `verified-packages.kt` | Packages confirmed safe on the Magic7 Pro (empty until checked on device) |
| `debloat-view-model.kt`, `debloat-screen.kt` | Compose list, search, enabled/disabled filter, confirm dialog, journal export |


## `:feature:permissions` (`feature/permissions/src/main/kotlin/vn/hongducdev/honortoolkit/feature/permissions`)

| File | Role |
|---|---|
| `permission-parsers.kt` | Parses user 0 `runtime permissions:` from `dumpsys package` and `cmd appops get` output |
| `permission-guard.kt` | Name validation; only third-party apps without a shared UID; SYSTEM_FIXED / POLICY_FIXED permissions are read-only |
| `permissions-controller.kt` | `setGranted` (`pm grant/revoke --user 0`) and `setAppOp` (`cmd appops set --user 0`); every change is read back and reported as failed if the ROM ignored it |
| `permissions-journal.kt` | Append-only `files/permissions-journal.tsv` with previous value, export text with PC revert commands |
| `permissions-view-model.kt`, `permissions-screen.kt` | App list, per-app permission switches, collapsible AppOps section |

## `:feature:circle-search` (`feature/circle-search/src/main/kotlin/vn/hongducdev/honortoolkit/feature/circlesearch`)

| File | Concern |
| --- | --- |
| `circle-search-launcher.kt`, `trigger-runner.kt` | Circle and Gemini assist sessions through the hidden API, delayed run, one retry after a Google warm-up, vibrate, last result, log of each attempt with the assistant that received it |
| `google-warm-up.kt` | Touches Google's providers, binds its SSB service and sends the wake-up broadcast; prewarms at most every 5 s |
| `trigger-log.kt` | Last 20 attempts (source, detail, OK/FAIL) in SharedPreferences so a refused launch can be copied |
| `trigger-activity.kt`, `trigger-tile-service.kt` | Non-launcher entry point and its Quick Settings tile |
| `nav-bar-strip-service.kt`, `strip-toggle-tile-service.kt`, `boot-receiver.kt` | Opt-in overlay strip as a foreground `specialUse` service with a quiet notification, 30 s health check, hides itself before the assist launch so it stays out of the screenshot; runs the mapped tap/double/hold action, its tile and restore after boot |
| `trigger-config.kt`, `strip-gestures.kt` | SharedPreferences for delays, vibration, strip geometry and per-gesture action (`none`/`circle`/`gemini`/`assistant`) |
| `wake-word-service.kt`, `wake-audio.kt`, `wake-filter.kt`, `wake-level.kt`, `wake-phrase.kt`, `wake-config.kt` | Opt-in offline "OK Google" wake word: Vosk foreground `microphone` service, 16 kHz audio loop, distractor-rich grammar with only `okay google` accepted, decoded-sample stability window, final-corroborated-by-partial filter, 5 s cooldown, loudness and playback gates, release-mic → open Gemini → rearm state machine, preferences and status shown on the Wake page |
| `assets/model-en/` | US English Vosk mobile model (Alpha Cephei), unpacked on first use by `StorageService` |
| `assistant-setup.kt` | Set Google as assistant through `AdbShell`, remember and restore the previous holder |
| `launcher-alias.kt`, `quick-launch-activities.kt` | Opt-in extra launcher icons (Circle to Search, Gemini, Search Live) for pickers that only list apps; Gemini opens the assist session and falls back to `ACTION_VOICE_COMMAND`, Search Live starts one public intent |
| `circle-search-view-model.kt`, `circle-search-screen.kt` | Compose hub (readiness card + links) with sub-pages: trigger, launcher icons, nav-bar strip |

## `:feature:update` (`feature/update/src/main/kotlin/vn/hongducdev/honortoolkit/feature/update`)

| File | Concern |
| --- | --- |
| `app-version.kt` | Tag parsing (`v0.2.0`, prerelease) and numeric version comparison |
| `github-release.kt` | Release/asset models, `parseLatestRelease` (prefers the release APK over debug/unsigned assets) |
| `github-release-client.kt` | `HttpURLConnection` calls for `releases/latest` and the APK; progress, size and SHA-256 checks |
| `update-config.kt` | Auto-check switch, last check time and the skipped version |
| `update-controller.kt` | Installed version, check, download with package check, `FileProvider` installer and unknown-sources setting |
| `update-view-model.kt` | Shared state: check, download, install, skip, auto-check |
| `update-screen.kt` | Update page: version, notes, progress, install, skip |
| `update-home-row.kt` | Home banner and tools row |
| `src/test` | Version comparison and release JSON parsing |

## Docs and plans

`docs/` holds these notes. `plans/` holds the plan and phase checklists.
