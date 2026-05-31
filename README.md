# TTS Reader

A personal Android app that turns shared articles into a clean, readable view —
and, ultimately, reads them aloud. Share a URL or text from Instapaper, your
browser, or any app; TTS Reader fetches it, strips the clutter, and shows the
article with its images and tables intact.

> **Status:** foundation slice. Sharing, extraction, and rich display work today.
> Text-to-speech playback and a saved library are planned next (see
> [Roadmap](#roadmap)). There is **no audio yet**.

## Features

- **Share-to-read.** Registers as a share target for links and text — send a page
  from Instapaper or Chrome straight into the app.
- **Readable extraction.** Uses a Readability-style parser to pull the main
  article and drop navigation, ads, and share/subscribe boilerplate.
- **See ≠ hear by design.** Images, tables, and code blocks stay visible on screen
  while the prose is structured into sentences for future text-to-speech.
- **Graceful failures.** Paywalled or unreachable pages show a clear message
  instead of crashing.

## Requirements

- macOS with [Homebrew](https://brew.sh/)
- JDK 17 and the Android command-line tools (no Android Studio needed):
  ```bash
  brew install --cask temurin@17 android-commandlinetools
  ```
- An Android device, or the bundled Pixel 7 / Android 16 emulator (provisioned by
  the build script).

## Build & run

Everything goes through `build.sh` — no IDE required.

```bash
# Build the debug APK (installs/accepts SDK packages on first run)
./build.sh

# Provision and boot the Pixel 7 / API 36 emulator
./build.sh --emulator

# Build and install onto the running emulator/device
./build.sh --install
```

### Try it

The app is built around *receiving* shares, so launching it from the app drawer
just shows an idle prompt. To see it work, share something in. On the emulator:
**Chrome → any article → ⋮ → Share → TTS Reader**, or from a terminal:

```bash
~/android-sdk/platform-tools/adb shell am start -a android.intent.action.SEND \
  -t text/plain \
  --es android.intent.extra.TEXT "https://en.wikipedia.org/wiki/Espresso" \
  -n com.ttsreader/.ingest.ShareReceiverActivity
```

## Tests

Pure logic (extraction, sentence segmentation, input classification) is unit-
tested on the JVM with offline HTML fixtures:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/android-sdk"
./gradlew :app:testDebugUnitTest

# A single class
./gradlew :app:testDebugUnitTest --tests "com.ttsreader.ingest.ArticleParserTest"
```

The Compose UI and the share flow are verified manually on the emulator.

## How it works

```
Share intent  →  ShareReceiverActivity
                   → ArticleExtractor      (URL vs text; fetch via OkHttp)
                       → ArticleParser     (readability4j + jsoup → typed blocks)
                           → SentenceSegmenter (prose → sentences with offsets)
                   → MainActivity → ReaderScreen (renders the blocks)
```

Extraction produces an ordered list of typed content blocks. Spoken blocks
(headings, paragraphs, quotes) carry sentences with character offsets; visual
blocks (images, tables, code) are shown but not read. The sentence is the atomic
unit that will drive speech and the follow-along highlight in the next slice.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · readability4j + jsoup · OkHttp · Coil ·
Android 16 (API 36), minSdk 26 · Gradle (no Android Studio).

## Roadmap

1. ✅ Build toolchain + share → extract → rich display *(this slice)*
2. On-device text-to-speech with sentence-level highlight
3. Background + lock-screen playback (foreground service + MediaSession)
4. Saved library with resume-where-you-left-off (Room)
5. Speech normalization (abbreviations, numbers, symbols)
6. Optional cloud neural voices

Design details and the implementation plans live in [`docs/superpowers/`](docs/superpowers/).
Contributor/agent guidance is in [`CLAUDE.md`](CLAUDE.md).
