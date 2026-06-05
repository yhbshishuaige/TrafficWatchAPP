#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DEV_HOME="${ANDROID_DEV_HOME:-$HOME/.local/share/android-toolchain}"
GRADLE_VERSION="8.10.2"

export JAVA_HOME="$ANDROID_DEV_HOME/jdk"
export ANDROID_SDK_ROOT="$ANDROID_DEV_HOME/android-sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

cd "$ROOT"
if [ -x "$ANDROID_DEV_HOME/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  "$ANDROID_DEV_HOME/gradle-$GRADLE_VERSION/bin/gradle" :app:assembleDebug
else
  ./gradlew :app:assembleDebug
fi
