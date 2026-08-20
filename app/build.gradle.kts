plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.vault.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vault.app"
        // Jetpack Security's EncryptedSharedPreferences (Android Keystore-backed)
        // is the whole reason the session token is safe to hold on-device at
        // rest — minSdk 26 is chosen to match, not raised arbitrarily; it also
        // happens to be the first API level adaptive launcher icons work at,
        // which is why there's no legacy round/square icon fallback below.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-milestone1"
    }

    buildTypes {
        debug {
            // This build is CI-produced and unsigned beyond Android's default
            // debug key (auto-generated per machine) — see the workflow and
            // README for why, and what changes if/when a signed release is
            // wanted later.
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No signingConfig: `assembleRelease` is intentionally left
            // unconfigured/unused by CI right now (see README "Signed
            // release" section for what's needed to turn this on).
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Thumbnails: image loading, caching, and downsampling-to-target-size
    // for the file browser's file/folder rows. Justification per SKILL.md
    // "every dependency must justify itself": hand-rolling bitmap
    // decoding, an LRU memory cache, and a disk cache correctly (without
    // OOMing on a large photo) is a lot of surface area to get wrong for
    // something this well-trodden. Coil 3.x specifically (io.coil-kt.coil3,
    // not the legacy io.coil-kt 2.x line) is Kotlin-first, coroutine-native
    // (fits this app's existing suspend-based repository layer directly).
    // coil-core no longer bundles a network fetcher by default in 3.x —
    // irrelevant here, since thumbnails are fetched through this app's own
    // authenticated Retrofit session (see ThumbnailFetcher.kt), not a
    // plain URL, so no coil-network-* artifact is needed at all.
    //
    // PINNED TO 3.3.0, NOT THE LATEST 3.5.0 — this project's compileSdk is
    // 34 and AGP 8.7.0 caps out at compileSdk 35; Coil 3.5.0's own AARs
    // require compileSdk 36, and its transitive AndroidX dependencies drag
    // several Compose libraries to versions requiring 35+, breaking
    // checkDebugAarMetadata project-wide (confirmed by a real CI run, not
    // a theoretical concern). 3.3.0 is confirmed to exist as a real
    // published release; unlike everything else added this session, its
    // exact compileSdk requirement was NOT independently confirmed via
    // search before picking it — if checkDebugAarMetadata still fails on
    // this, the compileSdk number in that error pins down precisely how
    // much further to step back, or whether bumping this project's own
    // compileSdk/AGP version is the better trade after all.
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")

    // Networking. kotlinx.serialization over Moshi/Gson: it's JetBrains-
    // maintained (same org as the Kotlin compiler), needs no reflection at
    // runtime, and its converter plugs into Retrofit with one line.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // DI. Justification per SKILL.md "every dependency must justify itself":
    // Hilt is Google-maintained, the de facto standard for Android DI, and
    // replaces what would otherwise be a hand-rolled service locator with
    // more surface area to get wrong.
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Session storage: EncryptedSharedPreferences, AES256_GCM-wrapped by a
    // key that never leaves the Android Keystore. This is the only
    // first-party option Google ships for this (still versioned as alpha
    // upstream — see README security notes) and is preferred here over a
    // hand-rolled Keystore wrapper on correctness/security grounds: an
    // audited implementation beats a bespoke one even at an alpha tag.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
