#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
echo "=== Install side-by-side preview (does not replace the signed app) ==="
adb install -r "$APK"
adb shell pm path ir.jessi.finance.preview | grep -F 'package:'
adb shell am force-stop ir.jessi.finance.preview || true
adb logcat -c
adb shell am start -W -n ir.jessi.finance.preview/ir.jessi.finance.MainActivity
sleep 8
adb logcat -d -s AndroidRuntime:E > /tmp/jessi_preview_runtime.log
if grep -E 'FATAL EXCEPTION|Process: ir.jessi.finance.preview' /tmp/jessi_preview_runtime.log; then
  echo "Preview crashed on startup" >&2
  exit 1
fi
adb shell pidof ir.jessi.finance.preview | grep -E '[0-9]+'
echo "JESSIE_PREVIEW_ANDROID_INSTALL_AND_LAUNCH_OK"
