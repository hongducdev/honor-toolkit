# Codebase summary

Entry points and where each concern lives. Architecture and rationale: `system-architecture.md`.

## `:app` (`app/src/main/kotlin/vn/hongducdev/honortoolkit`)

| File | Concern |
| --- | --- |
| `main-activity.kt` | Launcher activity, edge-to-edge, hosts `HomeScreen` |
| `ui/theme/honor-toolkit-theme.kt` | Material 3 theme with dynamic color on Android 12+ |
| `ui/home/home-view-model.kt` | `HomeUiState`, combines `AdbSession` flows, reconnects on start |
| `ui/home/home-screen.kt` | Scaffold, notification-permission request, shell probe card |
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

## Docs and plans

`docs/` holds these notes. `plans/` holds the plan and phase checklists.
