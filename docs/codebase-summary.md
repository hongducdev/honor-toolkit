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

## `:core:ui` (`core/ui/src/main/kotlin/vn/hongducdev/honortoolkit/core/ui`)

| File | Concern |
| --- | --- |
| `toolkit-motion.kt` | `rememberMotionEnabled`: off in battery saver or when animations are disabled |
| `scroll-motion-blur-modifier.kt` | AGSL vertical blur proportional to scroll speed (Android 13+, no-op below) |
| `wavy-progress-indicator.kt` | Animated sine-wave indeterminate progress |
| `cookie-shape.kt` | Scalloped badge shape from graphics-shapes |

## `:feature:debloat` (`feature/debloat/src/main/kotlin/vn/hongducdev/honortoolkit/feature/debloat`)

| File | Concern |
| --- | --- |
| `debloat-denylist.kt` | Hard denylist (exact names, prefixes) plus runtime "in use" packages |
| `package-scanner.kt` | `pm list packages -s [-d]`, labels, role holders and default IME lookup |
| `debloat-controller.kt` | `disable` / `enable` with name validation and denylist re-check |
| `debloat-journal.kt` | Append-only `files/debloat-journal.tsv`, export text with PC recovery commands |
| `package-name-validator.kt` | Rejects anything that is not a plain package name before it reaches a shell |
| `verified-packages.kt` | Packages confirmed safe on the Magic7 Pro (empty until checked on device) |
| `debloat-view-model.kt`, `debloat-screen.kt` | Compose list, search, enabled/disabled filter, confirm dialog, journal export |

## Docs and plans

`docs/` holds these notes. `plans/` holds the plan and phase checklists.
