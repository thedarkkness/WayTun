# Rebuilding xraycore.aar

`app/libs/xraycore.aar` is [Xray-core](https://github.com/XTLS/Xray-core) (tag in `build.sh`, currently
**v26.9.30**) behind a ~100-line gomobile wrapper (`wrapper/xraycore/xraycore.go`: Start/Stop from a JSON
config, the Android TUN fd handed over through Xray's own `xray.tun.fd`, and a `Protector` callback so the
app can `VpnService.protect()` Xray's sockets).

```sh
third_party/xray-build/build.sh      # needs Go (go.mod asks for 1.27+), Android NDK, ~1 GB of Go modules
```

## Why a patched gomobile

`libbox.aar` (sing-box) and any ordinary gomobile library both ship the Java classes `go.Seq`/`go.Universe`,
so two of them in one APK fail with "duplicate class". `gomobile-xgo.patch` renames that support package to
`xgo` (Java classes, JNI symbols and C lookups) in the SagerNet gomobile fork (v0.1.13); the result is built
into `bin/gomobile`/`bin/gobind` by `build.sh`. The native library is called `libxray.so`
(`-libname=xray`), distinct from `libbox.so`.

Each Go runtime still lives in its own Android process (`:xray`, `:singbox`, ...): two Go runtimes in one
process crash.

## Versions

Happ (Xray-based) ships Xray-core `v1.260327.1` + a September 2026 dev commit; we track the latest *stable*
tag that has been out for a while. Xray's Hysteria2 uses the `apernet/quic-go` fork, whose `chrome_parrot`
makes the QUIC handshake look like Chrome - something sing-box's Hysteria2 client doesn't do.
