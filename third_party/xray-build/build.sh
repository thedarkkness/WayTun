#!/bin/bash
# Rebuilds app/libs/xraycore.aar (Xray-core + WayTun's wrapper, arm64). See BUILD.md.
set -euo pipefail
cd "$(dirname "$0")"
XRAY_TAG=${XRAY_TAG:-v26.9.30}
GOMOBILE_VERSION=v0.1.13
[ -d ../xray-core ] || git clone --depth 1 --branch "$XRAY_TAG" https://github.com/XTLS/Xray-core.git ../xray-core
# gomobile fork with the support package renamed go -> xgo (so it can live next to libbox.aar)
if [ ! -d gomobile ]; then
  mod=$(cd wrapper && go list -m -f '{{.Dir}}' github.com/sagernet/gomobile 2>/dev/null || true)
  [ -n "$mod" ] || { (cd wrapper && go get github.com/sagernet/gomobile/bind); mod=$(cd wrapper && go list -m -f '{{.Dir}}' github.com/sagernet/gomobile); }
  cp -r "$mod" gomobile && chmod -R u+w gomobile
  (cd gomobile && patch -p1 < ../gomobile-xgo.patch >/dev/null)
fi
mkdir -p bin
(cd gomobile && go build -o ../bin/gomobile ./cmd/gomobile && go build -o ../bin/gobind ./cmd/gobind)
export PATH="$PWD/bin:$PATH"
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-$(ls -d "$ANDROID_HOME"/ndk/* | tail -1)}
cd wrapper
gomobile bind -v -o xraycore.aar -target android/arm64 -androidapi 29 \
  -javapkg=com.waytun.app.xray -libname=xray -trimpath -buildvcs=false \
  -ldflags "-checklinkname=0 -s -w -buildid=" ./xraycore
cp xraycore.aar ../../../app/libs/xraycore.aar
echo "done: app/libs/xraycore.aar"
