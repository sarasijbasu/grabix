#!/usr/bin/env bash
# Builds Grabix APKs on macOS / Linux.
# Needs: Java 17 and the Android SDK (installing Android Studio once gives you both).
set -euo pipefail
cd "$(dirname "$0")"

if [ -z "${ANDROID_HOME:-}" ]; then
  for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -d "$d" ] && export ANDROID_HOME="$d" && break
  done
fi
if [ -z "${ANDROID_HOME:-}" ]; then
  echo "Android SDK not found. Install Android Studio: https://developer.android.com/studio" >&2
  exit 1
fi
echo "sdk.dir=$ANDROID_HOME" > local.properties

chmod +x gradlew
./gradlew assembleRelease

echo
echo "Done. APKs are in app/build/outputs/apk/release/"
ls -lh app/build/outputs/apk/release/*.apk
