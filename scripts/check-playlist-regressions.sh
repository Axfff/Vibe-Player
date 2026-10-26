#!/usr/bin/env bash
set -euo pipefail

# The fixtures reset the DEBUG app's preferences. Use a disposable emulator overlay.
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to a disposable emulator serial}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
adb="$ANDROID_HOME/platform-tools/adb"
evidence_dir="${REGRESSION_EVIDENCE_DIR:-$repo_dir/project/app/build/playlist-regression-evidence}"
mkdir -p "$evidence_dir"
evidence_dir="$(cd "$evidence_dir" && pwd)"

if [[ "$("$adb" -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
    echo 'This suite resets debug-app data and requires a disposable Android emulator.' >&2
    exit 1
fi

collect_device_evidence() {
    "$adb" -s "$ANDROID_SERIAL" logcat -d -s PlaylistRegression:I AndroidRuntime:E \
        > "$evidence_dir/device-logcat.txt" 2>&1 || true
    "$adb" -s "$ANDROID_SERIAL" pull \
        /sdcard/Android/data/com.iven.musicplayergo.debug/files/regression-evidence \
        "$evidence_dir/screenshots" > "$evidence_dir/screenshots-pull.txt" 2>&1 || true
}
trap collect_device_evidence EXIT

cd "$repo_dir"
{
    printf 'source_commit=%s\n' "$(git rev-parse HEAD)"
    printf 'device_serial=%s\n' "$ANDROID_SERIAL"
    printf 'android_release=%s\n' "$("$adb" -s "$ANDROID_SERIAL" shell getprop ro.build.version.release | tr -d '\r')"
    printf 'android_api=%s\n' "$("$adb" -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
    printf 'abi=%s\n' "$("$adb" -s "$ANDROID_SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')"
    printf 'test_filter=%s\n' "${REGRESSION_TEST_CLASS:-com.iven.musicplayergo.regression (package)}"
    printf 'started_utc=%s\n' "$(date -u +%FT%TZ)"
    git status --porcelain -- project/app scripts
} > "$evidence_dir/environment.txt"
git diff HEAD -- project/app scripts > "$evidence_dir/source-diff.patch"

(
    cd project
    bash ./gradlew --no-daemon --console=plain \
        :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
) 2>&1 | tee "$evidence_dir/build.txt"

app_apk=project/app/build/outputs/apk/debug/app-debug.apk
test_apk=project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
shasum -a 256 "$app_apk" "$test_apk" > "$evidence_dir/apk-sha256.txt"
"$adb" -s "$ANDROID_SERIAL" install -r -t "$app_apk"
"$adb" -s "$ANDROID_SERIAL" install -r -t "$test_apk"
"$adb" -s "$ANDROID_SERIAL" logcat -c

filter=(-e package com.iven.musicplayergo.regression)
if [[ -n "${REGRESSION_TEST_CLASS:-}" ]]; then
    filter=(-e class "$REGRESSION_TEST_CLASS")
fi
"$adb" -s "$ANDROID_SERIAL" shell am instrument -w -r "${filter[@]}" \
    com.iven.musicplayergo.debug.test/androidx.test.runner.AndroidJUnitRunner \
    2>&1 | tee "$evidence_dir/instrumentation.txt"

# `am instrument` can exit zero even when JUnit fails or the process crashes.
if ! grep -Eq '^OK \([1-9][0-9]* tests?\)' "$evidence_dir/instrumentation.txt"; then
    echo "Android regression execution did not pass; inspect $evidence_dir/instrumentation.txt" >&2
    exit 1
fi
printf 'Evidence: %s\n' "$evidence_dir"
