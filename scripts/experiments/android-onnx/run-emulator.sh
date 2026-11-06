#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to a disposable emulator}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
case "$ANDROID_SERIAL" in emulator-*) ;; *) echo 'Only a disposable emulator is supported.' >&2; exit 1 ;; esac
adb="$ANDROID_HOME/platform-tools/adb"
[[ "$("$adb" -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || {
    echo 'Target is not an emulator.' >&2; exit 1;
}
here="$(cd "$(dirname "$0")" && pwd)"
apk="$here/app/build/outputs/apk/debug/app-debug.apk"
output="${1:-/tmp/vibe-android-onnx-result.json}"
pkg=org.vibe.onnxcheck
"$adb" -s "$ANDROID_SERIAL" install -r -t "$apk"
"$adb" -s "$ANDROID_SERIAL" shell am force-stop "$pkg"
"$adb" -s "$ANDROID_SERIAL" shell run-as "$pkg" rm -f files/result.json
"$adb" -s "$ANDROID_SERIAL" shell am start -W -n "$pkg/.MainActivity"
for ((n=0; n<60; n++)); do
    if "$adb" -s "$ANDROID_SERIAL" shell run-as "$pkg" cat files/result.json > "$output" 2>/dev/null && [[ -s "$output" ]]; then
        cat "$output"
        printf '\n'
        python3 -c 'import json,sys; result=json.load(open(sys.argv[1])); sys.exit(0 if result.get("passed") is True else 1)' "$output"
        exit
    fi
    sleep 1
done
echo 'Timed out waiting for the Android result.' >&2
exit 1
