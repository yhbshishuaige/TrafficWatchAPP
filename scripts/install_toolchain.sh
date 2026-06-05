#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLING="${ANDROID_DEV_HOME:-$HOME/.local/share/android-toolchain}"
JDK="$TOOLING/jdk"
SDK="$TOOLING/android-sdk"
GRADLE_VERSION="8.10.2"
GRADLE_HOME="$TOOLING/gradle-$GRADLE_VERSION"

mkdir -p "$TOOLING/downloads" "$SDK/cmdline-tools"

if [ ! -x "$JDK/bin/java" ]; then
  mkdir -p "$TOOLING/jdk-tmp"
  curl -L --fail --retry 3 \
    -o "$TOOLING/downloads/jdk17.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar -xzf "$TOOLING/downloads/jdk17.tar.gz" -C "$TOOLING/jdk-tmp" --strip-components=1
  rm -rf "$JDK"
  mv "$TOOLING/jdk-tmp" "$JDK"
fi

export JAVA_HOME="$JDK"
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -L --fail --retry 3 \
    -o "$TOOLING/downloads/commandlinetools-linux.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  rm -rf "$TOOLING/cmdline-tools-tmp" "$SDK/cmdline-tools/latest"
  mkdir -p "$TOOLING/cmdline-tools-tmp"
  unzip -q "$TOOLING/downloads/commandlinetools-linux.zip" -d "$TOOLING/cmdline-tools-tmp"
  mv "$TOOLING/cmdline-tools-tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$TOOLING/cmdline-tools-tmp"
fi

"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null <<'EOF'
y
y
y
y
y
y
y
y
y
y
EOF

"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"

"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --list_installed

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  curl -L --fail --retry 3 \
    -o "$TOOLING/downloads/gradle-$GRADLE_VERSION-bin.zip" \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  rm -rf "$GRADLE_HOME"
  unzip -q "$TOOLING/downloads/gradle-$GRADLE_VERSION-bin.zip" -d "$TOOLING"
fi

printf 'sdk.dir=%s\n' "$SDK" > "$ROOT/local.properties"

cd "$ROOT"
if [ ! -f "$ROOT/gradlew" ]; then
  ANDROID_SDK_ROOT="$SDK" ANDROID_HOME="$SDK" "$GRADLE_HOME/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION"
fi
