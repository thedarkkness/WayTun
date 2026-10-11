#!/bin/bash
# Runs every link in <workdir>/links_all.txt through the phone: serve one link as a subscription,
# refresh it in the app, switch the (single) subscription tunnel on twice and check that real
# traffic reaches a host-local HTTP server through the tunnel and that the VPN is torn down after.
#
# Prereqs: sing-box server + subserver.py + `python3 -m http.server 8099` running, adb reverse set,
# test subscription added once in the app. The coordinates are for the list scrolled to the bottom
# (refresh button of the subscription card, its tunnel row) and for the hero's power button at the top
# (check with screenshots first; defaults fit a 1268x2756 screen with two standalone tunnels above).
# Usage: matrix.sh <workdir> <host-lan-ip> [RX RY TX TY PX PY]
W=$1; HOST=$2
RX=${3:-853}; RY=${4:-1235}; TX=${5:-552}; TY=${6:-2245}; PX=${7:-1028}; PY=${8:-644}
bottom() { adb shell input swipe 600 2300 600 300 200; sleep 1; }
top() { adb shell input swipe 600 400 600 2300 200; sleep 1; }
mapfile -t TAGS < "$W/tags.txt"
vpn() { adb shell dumpsys connectivity | grep 'VPN CONNECTED' | grep -c waytun; }
# wait up to $2 s until vpn() equals $1
await_vpn() { for _ in $(seq 1 $(( $2 * 2 ))); do [ "$(vpn)" = "$1" ] && return 0; sleep 0.5; done; return 1; }
http() { adb shell "(printf 'GET / HTTP/1.0\r\n\r\n'; sleep 4) | nc -w 10 $HOST 8099 | tail -1" 2>&1 | tr -d '\r'; }
adb shell am force-stop com.waytun.app
for i in "${!TAGS[@]}"; do
  [ $((i+1)) -lt ${START:-1} ] && continue
  n=$((i+1)); tag=${TAGS[$i]}
  sed -n "${n}p" "$W/links_all.txt" > "$W/links.txt"
  adb shell am start -n com.waytun.app/.MainActivity >/dev/null 2>&1; sleep 2
  bottom; adb shell input tap $RX $RY; sleep 3; adb shell input tap $TX $TY; sleep 1; top
  adb logcat -c
  res=""
  for c in 1 2; do
    adb shell input tap $PX $PY; await_vpn 1 15; sleep 3
    up=$(vpn)
    r=$(http)
    adb shell input tap $PX $PY; await_vpn 0 15
    down=$(vpn)
    res="$res | c$c up=$up http='$r' off=$down"
  done
  crash=$(adb logcat -d | grep -cE "FATAL EXCEPTION|fatal error:|SIGABRT")
  echo "T$(printf %02d $n) $tag$res | crashes=$crash"
done
