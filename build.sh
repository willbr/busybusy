#!/usr/bin/env bash
set -euo pipefail

# Run from the repo root regardless of where this script is invoked from.
cd "$(dirname "$0")"

# --- Toolchain locations (Homebrew layout) ---
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"

CMDLINE_TOOLS="${CMDLINE_TOOLS:-/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"

SDK_MANAGER="$CMDLINE_TOOLS/bin/sdkmanager"
EMULATOR="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"
AVD_NAME="pixel7_api36"

# sdkmanager takes --sdk_root (underscore). $SDKM is intentionally unquoted so
# word-splitting separates the binary from its flag; safe because these paths
# contain no spaces. avdmanager has NO sdk-root flag (it errors on both --sdk_root
# and --sdk-root) — it reads the location from the exported ANDROID_HOME above, so
# its calls below pass no sdk flag. Exporting ANDROID_HOME is also what stops the
# tools from dropping a stray cmdline-tools copy in the current directory.
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

# Writes the AVD config files directly. cmdline-tools 20.0's `avdmanager create avd`
# is broken in the Homebrew layout ("Package path is not valid ... null" even when
# sdkmanager confirms the system image is installed), so we author the AVD ourselves.
create_avd() {
  local img_rel="$1"
  local avd_home="$HOME/.android/avd"
  mkdir -p "$avd_home/$AVD_NAME.avd"
  cat > "$avd_home/$AVD_NAME.ini" <<EOF
avd.ini.encoding=UTF-8
path=$avd_home/$AVD_NAME.avd
path.rel=avd/$AVD_NAME.avd
target=android-36
EOF
  cat > "$avd_home/$AVD_NAME.avd/config.ini" <<EOF
avd.ini.encoding=UTF-8
AvdId=$AVD_NAME
PlayStore.enabled=false
abi.type=arm64-v8a
avd.ini.displayname=Pixel 7 API 36
hw.cpu.arch=arm64
hw.ramSize=2048
image.sysdir.1=$img_rel/
tag.display=Google APIs
tag.id=google_apis
hw.lcd.density=420
hw.lcd.height=2400
hw.lcd.width=1080
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
disk.dataPartition.size=6442450944
fastboot.forceColdBoot=no
EOF
}

provision_emulator() {
  local img; img="$(arch_image)"
  $SDKM "emulator" "$img"
  # img is e.g. "system-images;android-36;google_apis;arm64-v8a"; the on-disk
  # relative path swaps ';' for '/'.
  local img_rel="${img//;//}"
  if ! "$EMULATOR" -list-avds 2>/dev/null | grep -q "$AVD_NAME"; then
    create_avd "$img_rel"
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
