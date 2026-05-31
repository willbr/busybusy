# TTS Reader — Foundation Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android app (no Android Studio) that receives a shared URL or text, extracts the readable article into typed content blocks, and displays the rich article (images, tables, code visible) on a Pixel 7 / Android 16 emulator. No TTS yet.

**Architecture:** Single-module Gradle project built entirely from the command line via `build.sh` (cmdline-tools `sdkmanager` + Gradle wrapper). Pure-JVM logic (sentence segmentation, HTML→blocks parsing, URL/text detection) is TDD-tested with JUnit and offline HTML fixtures. The Compose UI renders the block list and is verified manually on the emulator.

**Tech Stack:** Kotlin 2.0, Jetpack Compose (Material 3), `readability4j` + jsoup (extraction), OkHttp (fetch), Coil (images), Java `BreakIterator` (sentences), JUnit (tests). Package: `com.ttsreader`.

**Spec:** `docs/superpowers/specs/2026-05-31-tts-reader-android-design.md` (covers Milestones 1–2 of that spec).

---

## File Structure

```
build.sh                              CLI build + emulator provisioning
settings.gradle.kts                   Gradle project + repos
build.gradle.kts                      root (plugin versions)
gradle.properties                     JVM + AndroidX flags
gradle/wrapper/                       pinned Gradle (gradlew)
gradle/libs.versions.toml             version catalog
local.properties                      sdk.dir (git-ignored)
.gitignore
app/build.gradle.kts                  app module config + deps
app/src/main/AndroidManifest.xml      app + share intent filter
app/src/main/java/com/ttsreader/
├── MainActivity.kt                   hosts Compose, routes to reader
├── data/
│   ├── ArticleBlock.kt               sealed block model + Sentence
│   └── ParsedArticle.kt              title + blocks
├── ingest/
│   ├── SentenceSegmenter.kt          text → List<Sentence>  (pure)
│   ├── ArticleParser.kt              HTML → ParsedArticle    (pure)
│   ├── HtmlFetcher.kt                URL → HTML (OkHttp)     (network)
│   ├── ShareInput.kt                 URL vs text detection   (pure)
│   ├── ArticleExtractor.kt           orchestrates fetch+parse / text
│   └── ShareReceiverActivity.kt      receives SEND intents
└── ui/
    ├── ReaderScreen.kt               renders blocks (Coil images, tables, code)
    └── theme/Theme.kt                Material 3 theme
app/src/test/java/com/ttsreader/
├── ingest/SentenceSegmenterTest.kt
├── ingest/ArticleParserTest.kt
├── ingest/ShareInputTest.kt
└── ingest/ArticleExtractorTest.kt
app/src/test/resources/fixtures/
├── simple-article.html
└── rich-article.html
```

---

## Task 1: Gradle project skeleton + build.sh (hello-world APK)

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/ttsreader/MainActivity.kt`, `app/src/main/java/com/ttsreader/ui/theme/Theme.kt`
- Create: `build.sh`, `.gitignore`
- Create: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Create `.gitignore`**

```gitignore
*.iml
.gradle/
/local.properties
/.idea
.DS_Store
/build
/captures
app/build/
.externalNativeBuild
.cxx
local.properties
*.apk
```

- [ ] **Step 2: Create the Gradle version catalog `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.9.1"
kotlin = "2.0.21"
coreKtx = "1.13.1"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
coil = "2.7.0"
okhttp = "4.12.0"
readability4j = "1.0.8"
jsoup = "1.18.3"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
readability4j = { group = "net.dankito.readability4j", name = "readability4j", version.ref = "readability4j" }
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 3: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TtsReader"
include(":app")
```

- [ ] **Step 4: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 5: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 6: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ttsreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ttsreader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.readability4j)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
}
```

- [ ] **Step 7: Create the Gradle wrapper properties `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 8: Generate the wrapper jar + scripts**

Run (requires a system `gradle`; if absent, `brew install gradle` first):
```bash
gradle wrapper --gradle-version 8.11.1
```
Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`. If you have no system Gradle, download the wrapper jar manually from the Gradle distribution and place it at `gradle/wrapper/gradle-wrapper.jar`, then copy a `gradlew` script. After this, `./gradlew --version` must print Gradle 8.11.1.

- [ ] **Step 9: Create the Material 3 theme `app/src/main/java/com/ttsreader/ui/theme/Theme.kt`**

```kotlin
package com.ttsreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun TtsReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}
```

- [ ] **Step 10: Create `app/src/main/java/com/ttsreader/MainActivity.kt` (hello world placeholder)**

```kotlin
package com.ttsreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ttsreader.ui.theme.TtsReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtsReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Text("TTS Reader", modifier = Modifier.padding(padding))
                }
            }
        }
    }
}
```

- [ ] **Step 11: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:label="TTS Reader"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 12: Create `build.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

# --- Toolchain locations (Homebrew layout) ---
# JDK 17 from temurin@17 cask:
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"

# cmdline-tools come from the read-only Homebrew cask, but sdkmanager installs
# platforms/emulator/system-images into a SEPARATE writable SDK root.
CMDLINE_TOOLS="${CMDLINE_TOOLS:-/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"

SDK_MANAGER="$CMDLINE_TOOLS/bin/sdkmanager"
AVD_MANAGER="$CMDLINE_TOOLS/bin/avdmanager"
EMULATOR="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"
AVD_NAME="pixel7_api36"

# Every sdkmanager/avdmanager call targets the writable root.
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

# Detect host arch for system image
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
  "$EMULATOR" -avd "$AVD_NAME" -netdelay none -netspeed full >/dev/null 2>&1 &
  "$ADB" wait-for-device
  # wait for full boot
  until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 2
  done
  echo "Emulator ready."
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
```

- [ ] **Step 13: Make build.sh executable and build the APK**

Run:
```bash
chmod +x build.sh && ./build.sh
```
Expected: downloads SDK packages, runs `assembleDebug`, prints `APK: app/build/outputs/apk/debug/app-debug.apk`. (Requires `cmdline-tools` installed under `$ANDROID_HOME`.) If AGP rejects compileSdk 36, bump `agp` in `libs.versions.toml` to the latest 8.x and re-run.

- [ ] **Step 14: Boot the emulator and install**

Run:
```bash
./build.sh --emulator   # in one terminal (stays running)
./build.sh --install    # in another
```
Expected: emulator boots to a Pixel 7 / API 36 home screen; install succeeds; launching "TTS Reader" shows the "TTS Reader" text.

- [ ] **Step 15: Commit**

```bash
git add -A
git commit -m "feat: Gradle skeleton, build.sh, emulator provisioning, hello-world APK"
```

---

## Task 2: Data models — ArticleBlock, Sentence, ParsedArticle

**Files:**
- Create: `app/src/main/java/com/ttsreader/data/ArticleBlock.kt`
- Create: `app/src/main/java/com/ttsreader/data/ParsedArticle.kt`

No test (pure data declarations; exercised by later tasks).

- [ ] **Step 1: Create `ArticleBlock.kt`**

```kotlin
package com.ttsreader.data

/** A single sentence within a spoken block, with offsets into the block's text. */
data class Sentence(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * One unit of article content in document order.
 * Spoken blocks (Heading, Paragraph, Quote) carry sentences for future TTS.
 * Visual blocks (Image, Table, CodeBlock) are displayed only.
 */
sealed interface ArticleBlock {
    data class Heading(val text: String, val sentences: List<Sentence>) : ArticleBlock
    data class Paragraph(val text: String, val sentences: List<Sentence>) : ArticleBlock
    data class Quote(val text: String, val sentences: List<Sentence>) : ArticleBlock
    data class Image(val url: String, val caption: String?) : ArticleBlock
    data class Table(val rows: List<List<String>>) : ArticleBlock
    data class CodeBlock(val text: String) : ArticleBlock
}
```

- [ ] **Step 2: Create `ParsedArticle.kt`**

```kotlin
package com.ttsreader.data

data class ParsedArticle(
    val title: String,
    val sourceUrl: String?,
    val blocks: List<ArticleBlock>,
)
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ttsreader/data/
git commit -m "feat: article block + sentence + parsed-article data models"
```

---

## Task 3: SentenceSegmenter (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/ttsreader/ingest/SentenceSegmenter.kt`
- Test: `app/src/test/java/com/ttsreader/ingest/SentenceSegmenterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttsreader.ingest

import com.ttsreader.data.Sentence
import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSegmenterTest {

    @Test
    fun `splits two simple sentences`() {
        val out = SentenceSegmenter.segment("Hello world. Goodbye world.")
        assertEquals(2, out.size)
        assertEquals("Hello world.", out[0].text)
        assertEquals("Goodbye world.", out[1].text)
    }

    @Test
    fun `offsets index back into the source string`() {
        val src = "First. Second."
        val out = SentenceSegmenter.segment(src)
        val first = out[0]
        assertEquals("First.", src.substring(first.startOffset, first.endOffset))
    }

    @Test
    fun `does not split on common abbreviation Dr`() {
        val out = SentenceSegmenter.segment("Dr. Smith arrived. He was late.")
        assertEquals(2, out.size)
        assertEquals("Dr. Smith arrived.", out[0].text)
    }

    @Test
    fun `blank input yields no sentences`() {
        assertEquals(0, SentenceSegmenter.segment("   ").size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttsreader.ingest.SentenceSegmenterTest"`
Expected: FAIL — `SentenceSegmenter` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.ttsreader.ingest

import com.ttsreader.data.Sentence
import java.text.BreakIterator
import java.util.Locale

object SentenceSegmenter {

    fun segment(text: String, locale: Locale = Locale.getDefault()): List<Sentence> {
        if (text.isBlank()) return emptyList()
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)
        val sentences = mutableListOf<Sentence>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val raw = text.substring(start, end)
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty()) {
                val leading = raw.indexOf(trimmed.first())
                val absStart = start + leading
                val absEnd = absStart + trimmed.length
                sentences.add(Sentence(trimmed, absStart, absEnd))
            }
            start = end
            end = iterator.next()
        }
        return sentences
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttsreader.ingest.SentenceSegmenterTest"`
Expected: PASS (all 4). Note: `BreakIterator` treats "Dr." as non-terminal in the default English locale; if the abbreviation test fails on the CI locale, the assertion documents expected English behavior — run with `-Duser.language=en`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ttsreader/ingest/SentenceSegmenter.kt app/src/test/java/com/ttsreader/ingest/SentenceSegmenterTest.kt
git commit -m "feat: sentence segmenter via BreakIterator with source offsets"
```

---

## Task 4: ArticleParser — HTML → ParsedArticle (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/ttsreader/ingest/ArticleParser.kt`
- Test: `app/src/test/java/com/ttsreader/ingest/ArticleParserTest.kt`
- Test fixtures: `app/src/test/resources/fixtures/simple-article.html`, `app/src/test/resources/fixtures/rich-article.html`

- [ ] **Step 1: Create fixture `app/src/test/resources/fixtures/simple-article.html`**

```html
<!DOCTYPE html>
<html>
<head><title>Simple Title</title></head>
<body>
  <nav>Home About Subscribe</nav>
  <article>
    <h1>Simple Title</h1>
    <p>This is the first paragraph. It has two sentences.</p>
    <p>Here is a second paragraph with one sentence.</p>
    <aside class="related">Related: other stuff</aside>
  </article>
  <footer>Share this on social media</footer>
</body>
</html>
```

- [ ] **Step 2: Create fixture `app/src/test/resources/fixtures/rich-article.html`**

```html
<!DOCTYPE html>
<html>
<head><title>Rich Title</title></head>
<body>
  <article>
    <h1>Rich Title</h1>
    <p>Intro paragraph here.</p>
    <figure>
      <img src="https://example.com/pic.png" alt="a picture">
      <figcaption>A caption</figcaption>
    </figure>
    <blockquote>A wise quote. It spans two sentences.</blockquote>
    <pre><code>val x = 1
println(x)</code></pre>
    <table>
      <tr><th>Name</th><th>Age</th></tr>
      <tr><td>Ada</td><td>36</td></tr>
    </table>
    <p>Closing paragraph.</p>
  </article>
</body>
</html>
```

- [ ] **Step 3: Write the failing test**

```kotlin
package com.ttsreader.ingest

import com.ttsreader.data.ArticleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/$name"))
            .bufferedReader().readText()

    @Test
    fun `extracts title and paragraphs from a simple article`() {
        val article = ArticleParser.parse(fixture("simple-article.html"), "https://example.com/a")
        assertEquals("Simple Title", article.title)
        val paragraphs = article.blocks.filterIsInstance<ArticleBlock.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertTrue(paragraphs[0].text.startsWith("This is the first paragraph"))
        // first paragraph segments into two sentences
        assertEquals(2, paragraphs[0].sentences.size)
    }

    @Test
    fun `drops nav and footer boilerplate`() {
        val article = ArticleParser.parse(fixture("simple-article.html"), "https://example.com/a")
        val allText = article.blocks.joinToString(" ") {
            when (it) {
                is ArticleBlock.Paragraph -> it.text
                is ArticleBlock.Heading -> it.text
                else -> ""
            }
        }
        assertTrue(!allText.contains("Subscribe"))
        assertTrue(!allText.contains("Share this on social media"))
    }

    @Test
    fun `preserves images, tables, quotes and code in document order`() {
        val article = ArticleParser.parse(fixture("rich-article.html"), "https://example.com/b")
        val types = article.blocks.map { it::class.simpleName }
        // Heading first, an Image, a Quote, a CodeBlock, a Table all present
        assertTrue(types.contains("Image"))
        assertTrue(types.contains("Quote"))
        assertTrue(types.contains("CodeBlock"))
        assertTrue(types.contains("Table"))

        val image = article.blocks.filterIsInstance<ArticleBlock.Image>().first()
        assertEquals("https://example.com/pic.png", image.url)
        assertEquals("A caption", image.caption)

        val table = article.blocks.filterIsInstance<ArticleBlock.Table>().first()
        assertEquals(listOf("Name", "Age"), table.rows[0])
        assertEquals(listOf("Ada", "36"), table.rows[1])

        val code = article.blocks.filterIsInstance<ArticleBlock.CodeBlock>().first()
        assertTrue(code.text.contains("println(x)"))
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttsreader.ingest.ArticleParserTest"`
Expected: FAIL — `ArticleParser` unresolved.

- [ ] **Step 5: Write the implementation**

`ArticleParser` runs readability4j to get cleaned content HTML, then walks the jsoup DOM in document order producing typed blocks. readability4j's cleaning removes nav/footer/aside boilerplate; we map the remaining structural nodes.

```kotlin
package com.ttsreader.ingest

import com.ttsreader.data.ArticleBlock
import com.ttsreader.data.ParsedArticle
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object ArticleParser {

    private val BLOCK_TAGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6",
        "p", "blockquote", "figure", "img", "pre", "table", "li",
    )

    fun parse(html: String, url: String?): ParsedArticle {
        val readability = Readability4J(url ?: "https://localhost/", html)
        val parsed = readability.parse()
        val title = parsed.title?.takeIf { it.isNotBlank() } ?: extractTitle(html) ?: "Untitled"

        val contentHtml = parsed.articleContent?.outerHtml() ?: html
        val doc = Jsoup.parse(contentHtml, url ?: "")
        val blocks = mutableListOf<ArticleBlock>()
        walk(doc.body() ?: doc, blocks)
        return ParsedArticle(title = title, sourceUrl = url, blocks = blocks)
    }

    private fun extractTitle(html: String): String? =
        Jsoup.parse(html).title().takeIf { it.isNotBlank() }

    private fun walk(root: Element, out: MutableList<ArticleBlock>) {
        for (el in root.children()) {
            when (el.tagName().lowercase()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> addText(el, out, ::heading)
                "p", "li" -> addText(el, out, ::paragraph)
                "blockquote" -> addText(el, out, ::quote)
                "pre" -> {
                    val code = el.wholeText().trimEnd()
                    if (code.isNotBlank()) out.add(ArticleBlock.CodeBlock(code))
                }
                "img" -> addImage(el, null, out)
                "figure" -> addFigure(el, out)
                "table" -> addTable(el, out)
                else -> if (el.children().isNotEmpty()) walk(el, out) // descend into wrappers
            }
        }
    }

    private inline fun addText(
        el: Element,
        out: MutableList<ArticleBlock>,
        make: (String) -> ArticleBlock,
    ) {
        val text = el.text().trim()
        if (text.isNotEmpty()) out.add(make(text))
    }

    private fun heading(text: String) =
        ArticleBlock.Heading(text, SentenceSegmenter.segment(text))

    private fun paragraph(text: String) =
        ArticleBlock.Paragraph(text, SentenceSegmenter.segment(text))

    private fun quote(text: String) =
        ArticleBlock.Quote(text, SentenceSegmenter.segment(text))

    private fun addFigure(figure: Element, out: MutableList<ArticleBlock>) {
        val img = figure.selectFirst("img")
        val caption = figure.selectFirst("figcaption")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        if (img != null) addImage(img, caption, out)
    }

    private fun addImage(img: Element, caption: String?, out: MutableList<ArticleBlock>) {
        val src = img.absUrl("src").ifBlank { img.attr("src") }
        if (src.isNotBlank()) out.add(ArticleBlock.Image(src, caption))
    }

    private fun addTable(table: Element, out: MutableList<ArticleBlock>) {
        val rows = table.select("tr").map { tr ->
            tr.select("th, td").map { it.text().trim() }
        }.filter { it.isNotEmpty() }
        if (rows.isNotEmpty()) out.add(ArticleBlock.Table(rows))
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttsreader.ingest.ArticleParserTest"`
Expected: PASS (all 3). If readability4j strips the `<figure>`/`<table>` from very short fixtures (it favors text-dense content), enlarge the fixture prose so Readability keeps the article body, then re-run. The block-mapping logic itself is what's under test.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ttsreader/ingest/ArticleParser.kt app/src/test/java/com/ttsreader/ingest/ArticleParserTest.kt app/src/test/resources/fixtures/
git commit -m "feat: HTML to typed-block article parser (readability4j + jsoup)"
```

---

## Task 5: ShareInput — URL vs text detection (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/ttsreader/ingest/ShareInput.kt`
- Test: `app/src/test/java/com/ttsreader/ingest/ShareInputTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttsreader.ingest

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareInputTest {

    @Test
    fun `bare http url is classified as url`() {
        assertEquals(
            ShareInput.Url("https://example.com/a"),
            ShareInput.classify("https://example.com/a"),
        )
    }

    @Test
    fun `url with surrounding whitespace is trimmed`() {
        assertEquals(
            ShareInput.Url("https://example.com/a"),
            ShareInput.classify("  https://example.com/a \n"),
        )
    }

    @Test
    fun `text containing a url but also prose is treated as text`() {
        val input = "Check this out https://example.com/a it is great"
        assertEquals(ShareInput.Text(input), ShareInput.classify(input))
    }

    @Test
    fun `plain text is classified as text`() {
        assertEquals(ShareInput.Text("just some words"), ShareInput.classify("just some words"))
    }

    @Test
    fun `blank input is Empty`() {
        assertEquals(ShareInput.Empty, ShareInput.classify("   "))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttsreader.ingest.ShareInputTest"`
Expected: FAIL — `ShareInput` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.ttsreader.ingest

sealed interface ShareInput {
    data class Url(val url: String) : ShareInput
    data class Text(val text: String) : ShareInput
    data object Empty : ShareInput

    companion object {
        fun classify(raw: String?): ShareInput {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return Empty
            val isSingleToken = trimmed.none { it.isWhitespace() }
            val looksLikeUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://")
            return if (isSingleToken && looksLikeUrl) Url(trimmed) else Text(trimmed)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttsreader.ingest.ShareInputTest"`
Expected: PASS (all 5).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ttsreader/ingest/ShareInput.kt app/src/test/java/com/ttsreader/ingest/ShareInputTest.kt
git commit -m "feat: classify shared input as URL, text, or empty"
```

---

## Task 6: HtmlFetcher + ArticleExtractor (orchestration, TDD with fake fetcher)

**Files:**
- Create: `app/src/main/java/com/ttsreader/ingest/HtmlFetcher.kt`
- Create: `app/src/main/java/com/ttsreader/ingest/ArticleExtractor.kt`
- Test: `app/src/test/java/com/ttsreader/ingest/ArticleExtractorTest.kt`

- [ ] **Step 1: Create the fetcher interface + OkHttp implementation `HtmlFetcher.kt`**

```kotlin
package com.ttsreader.ingest

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Fetches raw HTML for a URL. Abstracted so the extractor is unit-testable offline.
 *  `fun interface` enables SAM lambdas in tests, e.g. `HtmlFetcher { url -> "..." }`. */
fun interface HtmlFetcher {
    fun fetch(url: String): String
}

class OkHttpHtmlFetcher(
    private val client: OkHttpClient = defaultClient(),
) : HtmlFetcher {

    override fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            return response.body?.string() ?: error("Empty body for $url")
        }
    }

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
```

- [ ] **Step 2: Write the failing test (uses a fake fetcher, no network)**

```kotlin
package com.ttsreader.ingest

import com.ttsreader.data.ArticleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/$name"))
            .bufferedReader().readText()

    @Test
    fun `url input is fetched then parsed`() {
        val html = fixture("simple-article.html")
        val fetcher = HtmlFetcher { reqUrl ->
            assertEquals("https://example.com/a", reqUrl)
            html
        }
        val extractor = ArticleExtractor(fetcher)
        val result = extractor.extract("https://example.com/a")
        assertTrue(result is ExtractResult.Success)
        result as ExtractResult.Success
        assertEquals("Simple Title", result.article.title)
    }

    @Test
    fun `plain text input becomes a single paragraph article without fetching`() {
        val fetcher = HtmlFetcher { error("should not fetch for text input") }
        val extractor = ArticleExtractor(fetcher)
        val result = extractor.extract("Just some shared prose. Two sentences here.")
        assertTrue(result is ExtractResult.Success)
        result as ExtractResult.Success
        val paragraphs = result.article.blocks.filterIsInstance<ArticleBlock.Paragraph>()
        assertEquals(1, paragraphs.size)
        assertEquals(2, paragraphs[0].sentences.size)
    }

    @Test
    fun `fetch failure returns Failure carrying the raw input`() {
        val fetcher = HtmlFetcher { throw RuntimeException("no network") }
        val extractor = ArticleExtractor(fetcher)
        val result = extractor.extract("https://example.com/a")
        assertTrue(result is ExtractResult.Failure)
        result as ExtractResult.Failure
        assertEquals("https://example.com/a", result.rawInput)
    }

    @Test
    fun `empty input returns Failure`() {
        val extractor = ArticleExtractor { "" }
        assertTrue(extractor.extract("   ") is ExtractResult.Failure)
    }
}
```

Note: `HtmlFetcher { ... }` works because it is a single-method (SAM) interface.

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttsreader.ingest.ArticleExtractorTest"`
Expected: FAIL — `ArticleExtractor` / `ExtractResult` unresolved.

- [ ] **Step 4: Write the implementation `ArticleExtractor.kt`**

```kotlin
package com.ttsreader.ingest

import com.ttsreader.data.ArticleBlock
import com.ttsreader.data.ParsedArticle

sealed interface ExtractResult {
    data class Success(val article: ParsedArticle) : ExtractResult
    /** Extraction failed; rawInput is preserved so the UI can offer to read it as-is. */
    data class Failure(val rawInput: String, val reason: String) : ExtractResult
}

class ArticleExtractor(
    private val fetcher: HtmlFetcher,
) {
    fun extract(raw: String?): ExtractResult {
        return when (val input = ShareInput.classify(raw)) {
            is ShareInput.Empty -> ExtractResult.Failure(raw.orEmpty(), "Nothing was shared.")
            is ShareInput.Text -> ExtractResult.Success(textArticle(input.text))
            is ShareInput.Url -> extractUrl(input.url)
        }
    }

    private fun extractUrl(url: String): ExtractResult {
        return try {
            val html = fetcher.fetch(url)
            ExtractResult.Success(ArticleParser.parse(html, url))
        } catch (e: Exception) {
            ExtractResult.Failure(url, e.message ?: "Could not fetch the page.")
        }
    }

    private fun textArticle(text: String): ParsedArticle {
        val paragraph = ArticleBlock.Paragraph(text, SentenceSegmenter.segment(text))
        val title = text.take(60).substringBefore('.').trim().ifBlank { "Shared text" }
        return ParsedArticle(title = title, sourceUrl = null, blocks = listOf(paragraph))
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttsreader.ingest.ArticleExtractorTest"`
Expected: PASS (all 4).

- [ ] **Step 6: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — all suites green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ttsreader/ingest/HtmlFetcher.kt app/src/main/java/com/ttsreader/ingest/ArticleExtractor.kt app/src/test/java/com/ttsreader/ingest/ArticleExtractorTest.kt
git commit -m "feat: article extractor orchestrating fetch+parse with text fallback"
```

---

## Task 7: ReaderScreen — render blocks in Compose

**Files:**
- Create: `app/src/main/java/com/ttsreader/ui/ReaderScreen.kt`

No unit test (Compose UI verified manually on the emulator in Task 9).

- [ ] **Step 1: Create `ReaderScreen.kt`**

```kotlin
package com.ttsreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ttsreader.data.ArticleBlock
import com.ttsreader.data.ParsedArticle

@Composable
fun ReaderScreen(article: ParsedArticle, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Text(
                text = article.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        items(article.blocks) { block -> BlockView(block) }
    }
}

@Composable
private fun BlockView(block: ArticleBlock) {
    when (block) {
        is ArticleBlock.Heading -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )

        is ArticleBlock.Paragraph -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        is ArticleBlock.Quote -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        )

        is ArticleBlock.Image -> Column(Modifier.padding(vertical = 8.dp)) {
            AsyncImage(
                model = block.url,
                contentDescription = block.caption,
                modifier = Modifier.fillMaxWidth(),
            )
            block.caption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        is ArticleBlock.CodeBlock -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        )

        is ArticleBlock.Table -> Column(
            Modifier
                .padding(vertical = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            block.rows.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(8.dp),
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ttsreader/ui/ReaderScreen.kt
git commit -m "feat: ReaderScreen renders article blocks (images, tables, code, quotes)"
```

---

## Task 8: ShareReceiverActivity + manifest wiring

**Files:**
- Create: `app/src/main/java/com/ttsreader/ingest/ShareReceiverActivity.kt`
- Modify: `app/src/main/java/com/ttsreader/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

For this slice, the shared article is handed to `MainActivity` in-process via a
companion holder (no Room yet — persistence arrives in a later plan).

- [ ] **Step 1: Add an in-memory handoff + reader rendering to `MainActivity.kt` (replace file contents)**

```kotlin
package com.ttsreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttsreader.data.ParsedArticle
import com.ttsreader.ingest.ExtractResult
import com.ttsreader.ui.ReaderScreen
import com.ttsreader.ui.theme.TtsReaderTheme

/** Process-scoped handoff from ShareReceiverActivity to MainActivity (no persistence yet). */
object CurrentArticle {
    @Volatile var value: ParsedArticle? = null
    @Volatile var failure: ExtractResult.Failure? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtsReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    val article = CurrentArticle.value
                    val failure = CurrentArticle.failure
                    when {
                        article != null -> ReaderScreen(article, Modifier.padding(padding))
                        failure != null -> FailureView(failure, Modifier.padding(padding))
                        else -> Text(
                            "Share an article here to read it.",
                            modifier = Modifier.padding(padding).padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FailureView(failure: ExtractResult.Failure, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp)) {
        Text("Couldn't extract a readable article.", style = MaterialTheme.typography.titleMedium)
        Text(failure.reason, modifier = Modifier.padding(top = 8.dp))
    }
}
```

- [ ] **Step 2: Create `ShareReceiverActivity.kt`**

```kotlin
package com.ttsreader.ingest

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.ttsreader.CurrentArticle
import com.ttsreader.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Transparent activity registered for SEND intents; extracts then launches the reader. */
class ShareReceiverActivity : ComponentActivity() {

    private val extractor by lazy { ArticleExtractor(OkHttpHtmlFetcher()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) { extractor.extract(shared) }
            when (result) {
                is ExtractResult.Success -> {
                    CurrentArticle.value = result.article
                    CurrentArticle.failure = null
                }
                is ExtractResult.Failure -> {
                    CurrentArticle.value = null
                    CurrentArticle.failure = result
                }
            }
            startActivity(
                Intent(this@ShareReceiverActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            finish()
        }
    }
}
```

- [ ] **Step 3: Add the coroutines dependency to `gradle/libs.versions.toml`**

Under `[versions]` add:
```toml
coroutines = "1.9.0"
```
Under `[libraries]` add:
```toml
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
```
Then in `app/build.gradle.kts` add to `dependencies`:
```kotlin
    implementation(libs.kotlinx.coroutines.android)
```

- [ ] **Step 4: Register the share activity in `AndroidManifest.xml`**

Add inside `<application>`, after the `MainActivity` block:
```xml
        <activity
            android:name=".ingest.ShareReceiverActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar"
            android:excludeFromRecents="true">
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: ShareReceiverActivity intake + in-process handoff to reader"
```

---

## Task 9: End-to-end manual verification on the emulator

**Files:** none (verification only).

- [ ] **Step 1: Build and install**

Run (emulator already booted from Task 1, else `./build.sh --emulator` first):
```bash
./build.sh --install
```
Expected: `Success` from adb.

- [ ] **Step 2: Verify share-a-URL flow**

In the emulator, open Chrome, go to a normal article page, tap the page menu → Share → **TTS Reader**.
Expected: the app opens showing the article title and cleaned prose; any images render; tables/code (if present) are visible; nav/ads/share-buttons are gone.

- [ ] **Step 3: Verify share-text flow**

In Chrome, long-press to select a sentence or two → Share → **TTS Reader**.
Expected: the app opens showing the shared text as a paragraph.

- [ ] **Step 4: Verify failure handling**

Trigger a fetch failure (e.g. share an unreachable URL like `https://example.com/definitely-not-real-404page` after disabling emulator network, or share a malformed link).
Expected: the app shows "Couldn't extract a readable article." with a reason — no crash.

- [ ] **Step 5: Full clean build sanity check**

Run:
```bash
./gradlew clean :app:testDebugUnitTest :app:assembleDebug
```
Expected: all unit tests pass and a debug APK is produced.

- [ ] **Step 6: Commit a short verification note**

```bash
git commit --allow-empty -m "chore: verified foundation slice end-to-end on Pixel 7 / API 36 emulator"
```

---

## Notes for the Implementer

- **No Android Studio.** All builds go through `./build.sh` / `./gradlew`. If a Gradle/AGP/SDK version mismatch appears, the fix is almost always bumping `agp` (and possibly the Gradle wrapper version) in the version catalog — Android 16 / API 36 needs AGP 8.9+.
- **readability4j is content-density sensitive.** It may discard structural elements from tiny fixtures. The unit tests assert the *mapping* behavior; if Readability strips a fixture's body, enlarge the fixture prose rather than weakening the mapping logic.
- **This slice has no persistence and no TTS.** `CurrentArticle` is a deliberate temporary in-process handoff; it gets replaced by Room in the next plan (Milestone 5), and the foreground TTS service arrives in the plan after extraction is proven (Milestone 3).
- **The `SpeechProvider` seam, Room, and MediaSession are intentionally absent here** — they belong to later plans so this slice stays small and verifiable.
