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

## `:feature:debloat` (`feature/debloat/src/main/kotlin/vn/hongducdev/honortoolkit/feature/debloat`)

| File | Concern |
| --- | --- |
| `debloat-denylist.kt` | Hard denylist (exact names, prefixes) plus runtime "in use" packages |
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
| `circle-search-launcher.kt`, `trigger-runner.kt` | Hidden-API assist session launch, delayed run, vibrate, last result, log of each attempt with the assistant that received it |
| `trigger-log.kt` | Last 20 attempts (source, detail, OK/FAIL) in SharedPreferences so a refused launch can be copied |
| `trigger-activity.kt`, `trigger-tile-service.kt` | Non-launcher entry point and its Quick Settings tile |
| `nav-bar-strip-service.kt`, `strip-toggle-tile-service.kt`, `boot-receiver.kt` | Opt-in long-press overlay strip as a foreground `specialUse` service with a quiet notification, 30 s health check, hides itself before the assist launch so it stays out of the screenshot; its tile and restore after boot |
| `trigger-config.kt` | SharedPreferences for delays, vibration and strip geometry |
| `assistant-setup.kt` | Set Google as assistant through `AdbShell`, remember and restore the previous holder |
| `launcher-alias.kt`, `quick-launch-activities.kt` | Opt-in extra launcher icons (Circle to Search, Gemini, Search Live) for pickers that only list apps; Gemini and Search Live activities start one public intent |
| `circle-search-view-model.kt`, `circle-search-screen.kt` | Compose hub (readiness card + links) with sub-pages: trigger, launcher icons, nav-bar strip |

## Docs and plans

`docs/` holds these notes. `plans/` holds the plan and phase checklists.
