#!/usr/bin/env bash
set -euo pipefail
APK=app/build/outputs/apk/signed/Jessi.apk
test -s "$APK"
CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -1)
echo "=== Testing new Jessi permanent-signed release versionCode=$CODE ==="
adb shell getprop ro.build.version.sdk
if [[ "$CODE" -gt 18 ]]; then
  echo "=== Install permanent-signed v0.11.1 baseline for genuine in-place upgrade check ==="
  curl --fail --location --retry 3 --retry-delay 2 --silent --show-error \
    "https://github.com/pooya220/jessie/releases/download/v0.11.1/Jessi-v0.11.1.apk" \
    -o /tmp/jessi-permanent-v0.11.1.apk
  test -s /tmp/jessi-permanent-v0.11.1.apk
  adb install -r /tmp/jessi-permanent-v0.11.1.apk
else
  echo "=== First release using NEW permanent signing key: cold install test ==="
fi
adb install -r "$APK"
adb shell pm path ir.jessi.finance | grep -F 'package:'
adb shell dumpsys package ir.jessi.finance | grep -F "versionCode=$CODE" | head -1
adb shell pm grant ir.jessi.finance android.permission.RECEIVE_SMS
adb shell pm grant ir.jessi.finance android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start -W -n ir.jessi.finance/.MainActivity
sleep 8
adb logcat -d -s AndroidRuntime:E > /tmp/jessi-permanent-crashes.log
if grep -E 'FATAL EXCEPTION|Process: ir.jessi.finance' /tmp/jessi-permanent-crashes.log; then
  echo "Jessi crashed at launch" >&2
  exit 1
fi
adb shell pidof ir.jessi.finance | grep -E '[0-9]+'
echo "PERMANENT_SIGNING_INSTALL_TEST_OK"
