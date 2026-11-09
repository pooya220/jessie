#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/signed/Jessi.apk"
echo "=== Android API ==="
adb shell getprop ro.build.version.sdk
echo "=== Installing previous official v0.9.0 APK ==="
OLD_APK="/tmp/jessi-previous-0.9.0.apk"
curl --fail --location --retry 3 --retry-delay 2 --silent --show-error \
  "https://github.com/pooya220/jessie/releases/download/v0.9.0/Jessi-v0.9.0.apk" \
  -o "$OLD_APK"
test -s "$OLD_APK"
adb install -r "$OLD_APK"
adb shell am start -n ir.jessi.finance/.MainActivity
sleep 3
echo "=== Updating without uninstalling to Jessie v0.10.0 ==="
adb install -r "$APK"
adb shell dumpsys package ir.jessi.finance | grep 'versionCode=16' | head -1
echo "=== Verifying installed package ==="
adb shell pm path ir.jessi.finance | grep -F 'package:'
echo "=== Granting SMS and notification permissions for deterministic emulator startup ==="
adb shell pm grant ir.jessi.finance android.permission.RECEIVE_SMS
adb shell pm grant ir.jessi.finance android.permission.POST_NOTIFICATIONS
adb shell am force-stop ir.jessi.finance
echo "=== Starting Jessie main activity after permission onboarding ==="
adb logcat -c
adb shell am start -W -n ir.jessi.finance/.MainActivity
sleep 8
echo "=== Checking for runtime crashes ==="
adb logcat -d -s AndroidRuntime:E > /tmp/jessi_androidruntime.log
if grep -E 'FATAL EXCEPTION|Process: ir.jessi.finance' /tmp/jessi_androidruntime.log; then
  echo "Jessie crashed during startup" >&2
  exit 1
fi
echo "=== Verifying process remains alive ==="
adb shell pidof ir.jessi.finance | grep -E '[0-9]+'
echo "JESSIE_ANDROID_INSTALL_AND_LAUNCH_OK"
