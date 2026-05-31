#!/usr/bin/env bash
set -euo pipefail

# Run from the repo root regardless of where this script is invoked from.
cd "$(dirname "$0")"

# --- Toolchain locations (Homebrew layout) ---
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"

CMDLINE_TOOLS="${CMDLINE_TOOLS:-/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"

SDK_MANAGER="$CMDLINE_TOOLS/bin/sdkmanager"
AVD_MANAGER="$CMDLINE_TOOLS/bin/avdmanager"
EMULATOR="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"
AVD_NAME="pixel7_api36"

# $SDKM is used unquoted on purpose: word-splitting separates the binary from its
# --sdk_root flag. Safe because these paths contain no spaces in the documented layout.
SDKM="$SDK_MANAGER --sdk_root=$ANDROID_HOME"

ensure_sdk() {
  if [[ ! -x "$SDK_MANAGER" ]]; then
    echo "ERROR: sdkmanager not found at $SDK_MANAGER"
    echo "Install it with: brew install --cask android-commandlinetools"
    exit 1
  fi
  mkdir -p "$ANDROID_HOME"
  echo "sdk.dir=$ANDROID_HOME" > local.properties
  yes | $SDKM --licenses >/dev/null || true
  $SDKM "platform-tools" "platforms;android-36" "build-tools;36.0.0"
}

arch_image() {
  case "$(uname -m)" in
    arm64|aarch64) echo "system-images;android-36;google_apis;arm64-v8a" ;;
    *)             echo "system-images;android-36;google_apis;x86_64" ;;
  esac
}

provision_emulator() {
  local img; img="$(arch_image)"
  $SDKM "emulator" "$img"
  if ! "$AVD_MANAGER" list avd 2>/dev/null | grep -q "$AVD_NAME"; then
    echo "no" | "$AVD_MANAGER" create avd -n "$AVD_NAME" -k "$img" --device "pixel_7"
  fi
  echo "Booting $AVD_NAME ..."
  # nohup + disown so the emulator survives this script exiting ("stays running").
  nohup "$EMULATOR" -avd "$AVD_NAME" -netdelay none -netspeed full >/dev/null 2>&1 &
  disown
  "$ADB" wait-for-device
  until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 2
  done
  echo "Emulator ready (running in background; close it from the emulator window or 'adb emu kill')."
}

build() {
  ./gradlew assembleDebug
  echo "APK: app/build/outputs/apk/debug/app-debug.apk"
}

install() {
  "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
}

ensure_sdk
case "${1:-build}" in
  --emulator) provision_emulator ;;
  --install)  build; install ;;
  build|"")   build ;;
  *) echo "usage: ./build.sh [build|--emulator|--install]"; exit 1 ;;
esac
