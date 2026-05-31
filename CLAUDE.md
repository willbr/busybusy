# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A personal Android app (Kotlin + Jetpack Compose, package `com.ttsreader`) that
receives shared URLs/text from other apps (Instapaper, browsers), extracts and
cleans the article, and — eventually — reads it aloud with TTS. The end goal:
share → tidy for speech → read aloud. Current state is the **foundation slice**:
share → extract → display the rich article (images/tables/code visible). **No TTS
and no persistence yet** — those are future slices.

Design spec and the slice-by-slice plan live in `docs/superpowers/`:
- `specs/2026-05-31-tts-reader-android-design.md` — full design (read this for the
  intended TTS service, highlight model, persistence, and phasing).
- `plans/2026-05-31-tts-reader-foundation.md` — the executed foundation plan.

## Critical environment constraints

- **No Android Studio.** All builds go through `./build.sh` (which drives the
  Gradle wrapper + Homebrew `cmdline-tools`). Do not assume IDE-generated files.
- **Always commit directly to `master`.** Never create branches.
- **JDK 17 must be pinned explicitly** — a JDK 26 is also installed. `build.sh`
  resolves it via `/usr/libexec/java_home -v 17`; do the same when calling Gradle
  directly: `export JAVA_HOME="$(/usr/libexec/java_home -v 17)"`.

## Toolchain layout (Homebrew)

The cmdline-tools and the writable SDK are **separate directories** — this trips
up the Android tools:
- cmdline-tools (read-only): `/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin/`
- Writable SDK root: `ANDROID_HOME=~/android-sdk` (where platforms/build-tools/
  emulator/system-images get installed).
- `sdkmanager` calls **must** pass `--sdk_root=$ANDROID_HOME` (underscore).
- `avdmanager create avd` is **broken** in cmdline-tools 20.0 here ("Package path
  is not valid … null" even when the image is installed). `build.sh` works around
  this by **hand-authoring the AVD config** (`~/.android/avd/pixel7_api36.*`)
  rather than calling avdmanager.

## Commands

```bash
# Build the debug APK (also installs/accepts SDK packages on first run)
./build.sh

# Provision + boot the Pixel 7 / API 36 emulator (runs in background, survives exit)
./build.sh --emulator

# Build and install onto the running emulator/device
./build.sh --install

# Unit tests (pure-JVM logic). Must export JAVA_HOME first when calling Gradle directly:
export JAVA_HOME="$(/usr/libexec/java_home -v 17)" && export ANDROID_HOME="$HOME/android-sdk"
./gradlew :app:testDebugUnitTest

# Run a single test class / method
./gradlew :app:testDebugUnitTest --tests "com.ttsreader.ingest.ArticleParserTest"
./gradlew :app:testDebugUnitTest --tests "com.ttsreader.ingest.ShareInputTest.bare http url is classified as url"

# Just compile (fast feedback, no tests)
./gradlew :app:compileDebugKotlin

# Full clean verification
./gradlew clean :app:testDebugUnitTest :app:assembleDebug
```

### Driving the app on the emulator

The app is built around *receiving shares*, so launching it directly only shows
an idle prompt. To exercise the real flow, send a SEND intent:

```bash
ADB=~/android-sdk/platform-tools/adb
# Share a URL (fetch + extract path)
"$ADB" shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "https://en.wikipedia.org/wiki/Espresso" \
  -n com.ttsreader/.ingest.ShareReceiverActivity
# Force-stop between runs so a running instance doesn't absorb the next intent:
"$ADB" shell am force-stop com.ttsreader
```

Note: multi-word text extras break adb word-splitting — wrap the value so the
device shell sees it quoted (e.g. `"'some multi word text'"`).

## Architecture

The central idea is **what you see ≠ what you hear**. Extraction produces an
ordered list of typed `ArticleBlock`s; spoken blocks (`Heading`/`Paragraph`/
`Quote`) carry `Sentence`s with character offsets, while visual blocks
(`Image`/`Table`/`CodeBlock`) are display-only. The **sentence is the atomic unit**
that will later drive TTS chunking and the follow-along highlight — keep that
invariant when touching the data model.

Data flow (share → display):
```
ShareReceiverActivity (transparent, registered for ACTION_SEND)
  → ArticleExtractor.extract(raw)        ingest/ArticleExtractor.kt
      ├─ ShareInput.classify             URL vs text vs empty
      ├─ HtmlFetcher (OkHttp)            URL → HTML; fun interface for offline testing
      ├─ ArticleParser.parse             readability4j + jsoup → List<ArticleBlock>
      │     └─ SentenceSegmenter         BreakIterator → List<Sentence> w/ offsets
      └─ returns ExtractResult.Success|Failure (Failure keeps rawInput)
  → CurrentArticle (in-process handoff)  → MainActivity → ReaderScreen renders blocks
```

Layers under `app/src/main/java/com/ttsreader/`: `ingest/` (intake + extraction),
`data/` (block/sentence models), `ui/` (Compose). `SpeechProvider` is the planned
seam for swapping on-device vs cloud TTS — it does not exist yet but the design
keeps the service/UI independent of the TTS implementation.

### Things to know when extending

- **`CurrentArticle` is a temporary `object` handoff**, deliberately not persisted.
  The persistence slice will replace it with a Room-backed lookup by id passed via
  Intent extra. Don't build durable features on it.
- **`SentenceSegmenter` uses `java.text.BreakIterator`, which has NO abbreviation
  awareness** — it splits "Dr." / "U.S." into separate sentences. The test pins
  this real behavior; abbreviation-aware segmentation is deferred to Phase 2 speech
  normalization. Don't "fix" the segmenter to make the abbreviation case pass.
- **`ArticleParser.walk` flattens nested block-level elements** (e.g. `<p>` inside
  `<blockquote>` collapses into one Quote). Acceptable for Phase 1; revisit if real
  articles need nested structure.
- **readability4j is content-density sensitive** — it can discard structural
  elements from tiny HTML fixtures. If a parser test fails because Readability
  stripped content, enlarge the fixture prose rather than weakening the mapping
  logic or assertions. Fixtures live in `app/src/test/resources/fixtures/`.
- Pure logic (extraction, segmentation, classification) is unit-tested on the JVM
  with offline fixtures; Compose UI and the share flow are verified manually on the
  emulator.

## Versions

Targets Android 16 (compileSdk/targetSdk 36), minSdk 26, Kotlin 2.0.21, AGP 8.9.1,
Gradle 8.11.1. Dependencies are managed in `gradle/libs.versions.toml` (version
catalog). Android 16 / API 36 needs AGP 8.9+ — if a version mismatch appears, the
fix is usually bumping `agp` (and possibly the Gradle wrapper) there.
