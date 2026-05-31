# TTS Reader — Android App Design

**Date:** 2026-05-31
**Status:** Approved design, pending spec review

## Summary

A personal Android app that receives shared articles (from Instapaper, browsers,
or any app), extracts and tidies the readable content, and reads it aloud with
text-to-speech. The screen shows the full rich article — including images and
tables — while TTS reads only the prose. Articles are saved to a library and
remember where you stopped.

Built **without Android Studio**: command-line `cmdline-tools` + Gradle wrapper,
driven by a single `build.sh`.

## Goals

- Share a URL or text into the app from Instapaper and other sources.
- Extract the main article; keep images/tables/code visible, strip nav/ads/boilerplate.
- Read the prose aloud with on-device TTS (cloud neural voices as a later option).
- Show the full article on screen with the **current sentence highlighted** as it reads.
- Background + lock-screen playback (podcast-style).
- A saved library of articles, each resuming where you left off.
- Buildable and runnable entirely from the command line against a Pixel 7 / Android 16 emulator.

## Non-Goals (for now)

- Word-level highlighting (sentence-level only).
- iOS / cross-platform (native Kotlin only).
- Play Store distribution / accounts / sync.
- Cloud TTS in the first phase (designed for, but built last).

## Build & Tooling (no Android Studio)

Standard Gradle Android project. Everything an IDE would do is done from the shell.

**Prerequisites (installed once, manually):**
- JDK 17
- Android `cmdline-tools` (contains `sdkmanager`, `avdmanager`)

**Targets:**
- `compileSdk` / `targetSdk` = **36** (Android 16)
- `minSdk` = **26** (Android 8) — covers TextToSpeech callbacks, foreground services, MediaSession
- Kotlin + Jetpack Compose

**Project files (replace the IDE):**
- `gradlew` / `gradlew.bat` + `gradle/wrapper/` — pinned Gradle version, auto-downloaded
- `settings.gradle.kts`, root `build.gradle.kts`, `app/build.gradle.kts` (Kotlin DSL)
- `local.properties` — `sdk.dir=...` (git-ignored)
- `app/src/main/AndroidManifest.xml` + Kotlin sources

**`build.sh` (idempotent):**
1. Resolve `ANDROID_HOME` (default `~/android-sdk`).
2. `sdkmanager` install + license-accept: `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`.
3. `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
4. Flags:
   - `--emulator` — provision + boot the AVD (installs `emulator` + `system-images;android-36;google_apis;<arch>`, creates AVD `pixel7_api36` with the `pixel_7` device profile; auto-detect arm64-v8a vs x86_64).
   - `--install` — `adb install -r` to whatever device/emulator is connected.

**Emulator note:** on-device TTS voices on the emulator can be limited; the
`google_apis` image is chosen to maximize the chance Google TTS is present. Voice
data may need a one-time download in Settings. The real Pixel sounds better; app
code is identical.

## Architecture

Single-module app organized by responsibility.

```
app/src/main/java/<pkg>/
├── ingest/
│   ├── ShareReceiverActivity.kt   transparent activity registered for SEND intents
│   ├── ArticleExtractor.kt        URL → fetch HTML → Readability; or use shared text
│   └── ContentCleaner.kt          strip clutter + speech normalization (phased)
├── data/
│   ├── Article.kt, ArticleBlock.kt, Sentence.kt
│   ├── ArticleDao.kt, AppDatabase.kt   Room: library + resume position
│   └── ArticleRepository.kt
├── speech/
│   ├── PlaybackService.kt         foreground Service: owns TextToSpeech + MediaSession
│   ├── TtsEngine.kt               wraps TextToSpeech; sentence queue + callbacks
│   └── SpeechProvider.kt          interface; OnDeviceProvider now, CloudProvider later
└── ui/
    ├── LibraryScreen.kt           saved articles list
    ├── ReaderScreen.kt            rich article + sentence highlight + controls
    └── MainActivity.kt + ViewModels
```

### Display model vs speech model (key idea)

**What you see ≠ what you hear.** Extraction produces an ordered list of typed
content blocks:

```
ArticleBlock =
  | Heading(text)
  | Paragraph(sentences: List<Sentence>)
  | Image(url, caption?)        // displayed, not spoken
  | Table(rows)                 // displayed, not spoken
  | CodeBlock(text)             // displayed, not spoken
  | Quote(sentences)

Sentence = { text, startOffset, endOffset }   // offsets into the block's rendered text
```

- **Display side (`ReaderScreen`):** renders every block in document order —
  images via Coil, tables as real tables, code in a monospace box, paragraphs as
  text. Full visual article, scrollable.
- **Speech side (`TtsEngine`):** walks the same block list but speaks only
  `Heading` / `Paragraph` / `Quote`, sentence by sentence. Images, tables, and
  code are silently skipped (you see them; the view scrolls past as TTS moves on).

The **sentence is the atomic spoken + highlight unit**. Sentences live inside blocks.

## Data Flow (end to end)

1. **Share in.** Instapaper/Chrome "Share → app" launches `ShareReceiverActivity`
   (transparent, no visible UI). Reads `EXTRA_TEXT`: a URL or selected text.
2. **Extract.** `ArticleExtractor` auto-detects:
   - URL → OkHttp GET (desktop User-Agent, ~15s timeout) → `readability4j`
     extraction → title, byline, content HTML preserving img/table/figure nodes.
   - Plain text → used directly as a single Paragraph stream.
3. **Clean.** `ContentCleaner` removes nav/ads/share/subscribe/related boilerplate
   and footnote markers; keeps genuine content blocks. Builds the `List<ArticleBlock>`.
4. **Save.** Article (title, source URL, blocks, position = (0,0)) written to Room.
5. **Play.** Because the user wants both: immediately start `PlaybackService` on
   the article and open `ReaderScreen`; `ShareReceiverActivity` finishes itself.
6. **Speak + highlight.** `PlaybackService` (foreground, media notification) feeds
   spoken sentences to `TtsEngine` one utterance per sentence, tagged with
   (blockIndex, sentenceIndex). On each sentence boundary it advances the
   highlight (via exposed state) and saves position to Room. Lock-screen controls
   map to the same play/pause/skip actions.
7. **Resume.** Opening an article from `LibraryScreen` seeks the service to the
   saved `(blockIndex, sentenceIndex)`.

## Playback Architecture (Approach A)

A bound **foreground `Service`** owns the `TextToSpeech` engine and a
`MediaSession`. The UI binds to it to render state and the highlight; the service
keeps playing when backgrounded and publishes lock-screen controls. This is the
idiomatic Android pattern for podcast-style playback and the only clean way to get
reliable background + lock-screen behavior.

- **Single source of truth:** the service exposes
  `StateFlow<PlaybackState>` = `{ articleId, blockIndex, sentenceIndex, isPlaying }`.
  The UI never holds its own copy of "what's playing." This makes
  background↔foreground transitions reliable.
- **`SpeechProvider` interface** isolates TTS behind a seam. Ship `OnDeviceProvider`
  (`TextToSpeech`) first; cloud/neural voices later are a second implementation —
  service, UI, and highlight logic don't change.

### Highlight (sentence-level only)

Speak one sentence per utterance, tagging each `utteranceId` with its
(blockIndex, sentenceIndex). Use `TextToSpeech` `onStart` / `onDone` callbacks to
advance the highlight — these always work across engines (no dependence on
`onRangeStart`). `ReaderScreen` collects `PlaybackState`, highlights the current
sentence, and auto-scrolls to keep it in view. Tap a sentence → send its index to
the service → flush queue → re-speak from there.

## Cleanup & Normalization (phased)

- **Phase 1 (ships first):** Readability extraction → block list; strip
  nav/ads/share/subscribe/related boilerplate and footnote markers `[1]`; keep
  images/tables/code as display-only blocks; collapse whitespace. Sentence
  segmentation via `BreakIterator.getSentenceInstance()` (no extra dep,
  locale-aware, handles "Dr."/"U.S." reasonably).
- **Phase 2 (layered in after Phase 1 works):** speech normalization on spoken
  blocks only — expand common abbreviations (`Dr.`, `e.g.`), natural numbers/
  percent/currency, fix smart-quotes/dashes/ellipses. Rule-based, grown
  incrementally against real shared articles.

## Error Handling & Edge Cases

- **Extraction fails / paywalled / not an article:** ReaderScreen shows a clear
  state ("Couldn't extract readable text") with an option to read the raw shared
  text. Never crash the share.
- **No network on a URL share:** explain network is needed; fall back to shared
  text if present.
- **TTS not ready / no voice data:** init is async — queue the request; on
  `LANG_MISSING_DATA` surface a one-time prompt to install voice data (likely on
  emulator).
- **Audio focus:** request focus; pause on focus loss (incoming call) and on
  headphone unplug (`BECOMING_NOISY`). Resume is manual.
- **Service lifecycle:** foreground service with media-style notification so
  Android won't kill it mid-article; release `TextToSpeech` when playback ends and
  the UI is gone.
- **Position saving:** upsert resume position on every sentence boundary so a
  crash or swipe-away never loses the place.

## Dependencies

- Jetpack Compose (UI), Material 3
- Room (persistence)
- OkHttp (fetch URLs)
- `readability4j` (article extraction)
- Coil (image loading in the reader)
- AndroidX Media / MediaSession (lock-screen + background controls)
- Kotlin Coroutines / Flow

## Phasing / Milestones

1. **Build skeleton:** Gradle project, `build.sh`, emulator provisioning, hello-world APK on the AVD.
2. **Share + extract + display:** receive share, extract to blocks, render rich article (no TTS yet).
3. **TTS playback:** foreground service, on-device TTS, sentence highlight + auto-scroll, basic controls.
4. **Background + lock screen:** MediaSession, notification controls, audio focus.
5. **Library + resume:** Room persistence, library list, resume position.
6. **Speech normalization (Phase 2 cleanup).**
7. **(Later) Cloud neural voice provider.**

## Testing Approach

- **Unit tests (JVM, fast):** `ContentCleaner` / `ArticleExtractor` block
  production and sentence segmentation against saved HTML fixtures; speech
  normalization rules. These are the highest-value tests and need no device.
- **Instrumented/manual:** playback, highlight, background/lock-screen, and
  resume verified on the emulator (and real Pixel) — hard to fully automate given
  TTS hardware dependence.

## Open Questions

- Exact `readability4j` artifact/version and how faithfully it preserves
  table/figure nodes (validate during Phase 2 milestone).
- Whether emulator Google TTS voice is present out of the box or needs a one-time
  manual voice download.
