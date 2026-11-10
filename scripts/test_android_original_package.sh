#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/debug/app-debug.apk"
echo "=== Cold install original-package Jessie 0.11 on Android 13 ==="
adb shell getprop ro.build.version.sdk
test -s "$APK"
adb install -r "$APK"
adb shell pm path ir.jessi.finance | grep 'package:'
adb shell dumpsys package ir.jessi.finance | grep 'versionCode=17' | head -1
echo "=== Grant test permissions ==="
adb shell pm grant ir.jessi.finance android.permission.RECEIVE_SMS
adb shell pm grant ir.jessi.finance android.permission.POST_NOTIFICATIONS
adb logcat -c
echo "=== Launch original package ID ==="
adb shell am start -W -n ir.jessi.finance/.MainActivity
sleep 8
echo "=== Check runtime crashes ==="
adb logcat -d -s AndroidRuntime:E > /tmp/jessi_original_androidruntime.log
if grep -E 'FATAL EXCEPTION|Process: ir.jessi.finance' /tmp/jessi_original_androidruntime.log; then
  echo "Jessie crashed during startup" >&2
  exit 1
fi
adb shell pidof ir.jessi.finance | grep -E '[0-9]+'
echo "JESSIE_0_11_ORIGINAL_PACKAGE_COLD_INSTALL_OK"
echo "NOTE: temporary signer cannot update the existing permanent-signature release"
