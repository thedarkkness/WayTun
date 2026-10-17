# WayTun multiplatform plan

Goal: ship WayTun for Windows, Linux, macOS and (maybe) iOS from the same Kotlin code, without breaking
Android. Work happens on the `multiplatform` branch, stage by stage.

## Starting point (1.3.2, versionCode 7)

- Kotlin 2.2.10, AGP 9.4.0 (built-in Kotlin), Compose BOM 2026.02.01, Hilt, Room 2.8.5, Navigation Compose, Haze,
  CameraX + ML Kit. minSdk 29, targetSdk 37. No unit tests, no CI; releases are hand-uploaded APKs.
- Cores:
  - **Xray-core v26.9.30** behind a ~100-line gomobile wrapper (`third_party/xray-build/wrapper/xraycore`:
    `Start(configJSON, tunFd, assetDir)`, `Stop`, `IsRunning`, `Version`, `SetProtector`) → `app/libs/xraycore.aar`.
    Android hands its `VpnService` TUN fd to **Xray's own TUN inbound** (`xray.tun.fd`).
  - WireGuard: `com.wireguard.android:tunnel` (GoBackend).
  - AmneziaWG: vendored `third_party/amneziawg-android/tunnel`.
- **hev-socks5-tunnel is not used** (only a git-ignored scratch dir). Xray v26.9.30's TUN inbound
  (`proxy/tun`) already supports Linux, Windows (wintun + WFP leak protection), macOS (utun) and FreeBSD, so the
  desktop TUN mode doesn't need a separate tun2socks.
- **DataStore is declared but not used**: all settings live in SharedPreferences (`obfuscation`,
  `interface_style`, `app_theme`, `app_language`, `ui_prefs`).

## Target layout

```
shared/      KMP library (androidLibrary + jvm("desktop"); iOS targets later)
  commonMain     parsers, XrayConfigBuilder, obfuscation, Room KMP entities/DAOs/DB, subscriptions (Ktor),
                 GitHubReleaseApi (Ktor), AppLog core (secret scrubbing), TunnelSession, KeyValueStore,
                 Koin module for desktop/iOS
  jvmCommonMain  android + desktop: things that really need the JVM (javax.crypto, sockets, DNS)
  androidMain    Room migrations over SupportSQLite, SharedPreferences store, OkHttp engine
  desktopMain    BundledSQLiteDriver, Java HTTP engine, file-based stores
app/         Android app (VpnService, Keystore, CameraX, APK updater, Android UI for now)
desktopApp/  Compose Desktop (stage 2)
iosApp/      Xcode project + NEPacketTunnelProvider (stage 6, only after an explicit go-ahead)
```

Packages keep their current names (`com.waytun.app.core.parser`, ...) when files move into `shared`, so Android
code keeps its imports and the move is a pure relocation.

## Decisions

### Room KMP, data compatibility
Entities, DAOs and `AppDatabase` (version 7) move to `commonMain` with `@ConstructedBy`. Migrations are kept as
plain SQL step lists in common code. On **Android** they are applied through `Migration.migrate(SupportSQLiteDatabase)`
exactly as today: the same framework SQLite, the same `waytun.db` file, the same multi-process invalidation
(the VPN services open the database from their own processes). Desktop uses `BundledSQLiteDriver` and the
`migrate(SQLiteConnection)` path. A migration test opens a version-1 database and walks it to 7.

### Settings storage
Nothing to migrate from DataStore (it isn't used). Common code gets a small `KeyValueStore` interface; Android
implements it on top of **the same SharedPreferences files**, so user settings survive the update untouched.
Desktop gets a DataStore KMP (or file) implementation in stage 2. The unused DataStore dependency is removed.

### DI: Hilt now, Koin with the shared UI
Classes in `shared` use plain constructor injection, no DI annotations. `shared` ships a Koin module for desktop
and iOS. Android keeps Hilt for now and provides the shared classes with `@Provides`: converting ~30 ViewModels,
three VPN service processes and the receivers at the same time as the data layer would risk breaking Android for
no user-visible gain. The full Android switch to Koin happens in stage 2 together with moving the UI into
`shared`, where Compose Multiplatform ViewModels need a multiplatform DI anyway.

### Networking
`HttpURLConnection` → Ktor client: OkHttp engine on Android, Java engine on desktop (Darwin later). Same
headers (HWID, device, User-Agent), same timeouts, same "HTML page → retry as a known client" and
"`/json`, `/v2ray-json` explicit format path" fallbacks.

### JSON, Base64, logs
`org.json` → `kotlinx.serialization.json` (`JsonObject` builders; Xray doesn't care about key order).
`android.util.Base64` → `kotlin.io.encoding.Base64` (with the same lenient decoding of URL-safe / unpadded input).
`android.util.Log` → `AppLog` sinks; the secret scrubbing (UUIDs, keys, passwords, URLs) stays in common code.

### Desktop Xray: separate process (stage 2)
The desktop app runs the **official `xray` binary of the same pinned version** as a child process with the
config produced by the shared `XrayConfigBuilder`:
- crash isolation: a core crash never takes down the UI that owns the system-proxy/route state, and the UI can
  always roll that state back;
- the TUN mode (stage 3) needs a privileged helper/service anyway, and a process is the natural unit to start
  there with exactly the needed rights;
- no cgo/JNA ABI to maintain per OS and architecture; upstream release binaries and checksums can be used, or
  the same source tag built in CI;
- stats come from Xray's own `StatsService` API inbound.

iOS (and a macOS Network Extension, if ever) can't spawn processes, so there the same wrapper is built with
`gomobile bind -target=ios,iossimulator,macos` into an `.xcframework`.

### How tunnels come up per OS (later stages)
- **Windows**: v1 system proxy (Xray SOCKS/HTTP inbound on 127.0.0.1 + WinINet proxy via the registry, rolled
  back on exit and on crash, no admin). v2 TUN via Xray's TUN inbound (wintun) in a service with admin rights.
  WG/AWG through the embeddable dll services.
- **Linux**: TUN via Xray's TUN inbound inside a tiny helper with `CAP_NET_ADMIN` (systemd unit / polkit),
  talking to the unprivileged UI over a unix socket with a fixed command whitelist; DNS via systemd-resolved,
  routes and DNS rolled back on exit and after a crash. Packages: .deb and AppImage (no Flatpak: it gets in the
  way of TUN).
- **macOS**: v1 system proxy via `networksetup`; then TUN via a root helper (utun) or a Network Extension
  (needs an Apple Developer ID).
- **iOS**: NEPacketTunnelProvider only (~50 MB memory limit). App Store rule 5.4 requires an organization
  account and the Russian App Store removes VPNs on request: **stop and ask before any iOS work**.

## Stages

0. Branch, build check, this document.
1. `shared` module, Android behaviour unchanged (see decisions above), unit tests in `commonTest` on jvm.
2. Desktop app (Windows + Linux), system-proxy mode, desktop key storage (DPAPI / libsecret / Keychain, with an
   honest warning instead of silent plain-text storage), .msi / .deb / AppImage.
3. TUN: Linux helper, then Windows service; then WG/AWG on desktop.
4. CI: `.github/workflows/release.yml`, ubuntu/windows/macos matrix on `v*` tags, Android signing from secrets.
5. Desktop auto-update through `GitHubReleaseApi` with SHA-256 verification.
6. macOS, then iOS - only after an explicit go-ahead.

## Hard rules

- The Android signing key never leaves this machine: never committed, copied or printed. `applicationId`
  stays `com.waytun.app`; release builds are checked with `apksigner verify --print-certs` against
  SHA-256 `8EA7002E CE413B72 FFE06755 0DEC0371 5041A79A EAC8E4E0 D5B2BE76 D6B472FD`.
- No version bumps or GitHub releases without an explicit command.
- Privileged helpers: minimum rights, whitelisted commands, no shell strings built from user input.
- Logs keep scrubbing secrets.
