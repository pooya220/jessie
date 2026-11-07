#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/signed/Jessi.apk"
echo "=== Android API ==="
adb shell getprop ro.build.version.sdk
echo "=== Installing permanently signed Jessie APK ==="
adb install -r "$APK"
echo "=== Verifying installed package ==="
adb shell pm path ir.jessi.finance | grep -F 'package:'
echo "=== Starting Jessie main activity ==="
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
