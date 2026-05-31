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
        unitTests.all {
            // Pin the JVM locale for unit tests so BreakIterator uses the
            // intended English sentence-breaking rules (e.g. "Dr." is not a
            // sentence terminator). The forked test worker does not inherit
            // command-line -D flags and the host default may be en_GB.
            it.systemProperty("user.language", "en")
            it.systemProperty("user.country", "US")
        }
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
