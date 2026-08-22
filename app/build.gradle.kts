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
    // Bumped from 34 to 35, not because any app code needs newer APIs —
    // targetSdk/minSdk below are untouched, so runtime behavior doesn't
    // change at all. This is purely to satisfy checkDebugAarMetadata:
    // Coil 3.3.0 (see the dependencies block below) transitively pulls
    // Compose UI 1.8.2, newer than what compose-bom:2024.10.01 pins on
    // its own, and Compose 1.8.2's AARs require compileSdk 35+ — confirmed
    // by a real CI failure listing all 12 offending artifacts, not a
    // guess. AGP 8.7.0 already supports compileSdk up to 35 without any
    // AGP/Gradle version bump (35 was in fact the ceiling AGP 8.7.0's own
    // error output named) — this is a one-line, self-contained fix, not
    // the start of a bigger toolchain-upgrade cascade. The CI workflow's
    // sdkmanager step needs the matching `platforms;android-35` package
    // installed too, or this trades one failure for a different one.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vault.app"
        // Jetpack Security's EncryptedSharedPreferences (Android Keystore-backed)
        // is the whole reason the session token is safe to hold on-device at
        // rest — minSdk 26 is chosen to match, not raised arbitrarily; it also
        // happens to be the first API level adaptive launcher icons work at,
        // which is why there's no legacy round/square icon fallback below.
        minSdk = 26
        targetSdk = 34
        // Android refuses to install an update over an existing install
        // unless versionCode strictly increases — a static 1 here would
        // let a signed release install once, then silently fail every
        // update after. VERSION_CODE is set by the CI workflow to
        // github.run_number (auto-incrementing per workflow run,
        // guaranteed monotonic for this repo) — falls back to 1 for a
        // local build without it, matching the same graceful-degradation
        // pattern as the signingConfigs block below.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        // Cosmetic, unlike versionCode above — doesn't affect whether an
        // update installs, just what's shown to the user. Set by CI to
        // the pushed tag name; falls back to the milestone label locally.
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0-milestone1"
    }

    signingConfigs {
        create("release") {
            // Populated only when these env vars are present — set by the
            // CI workflow after decoding the base64 keystore secret to a
            // file (see android-release-apk.yml's "Decode keystore" step).
            // Left entirely unset for a local `gradle assembleRelease`
            // without them: AGP itself then refuses the build with its own
            // clear "signing config incomplete" error, rather than this
            // file NPE'ing on a null env var or silently producing an
            // unsigned/mis-signed APK.
            System.getenv("KEYSTORE_PATH")?.let { storeFile = file(it) }
            System.getenv("KEYSTORE_PASSWORD")?.let { storePassword = it }
            System.getenv("KEY_ALIAS")?.let { keyAlias = it }
            System.getenv("KEY_PASSWORD")?.let { keyPassword = it }
        }
    }

    buildTypes {
        debug {
            // This build is CI-produced and unsigned beyond Android's default
            // debug key (auto-generated per machine) — see android-debug-build.yml
            // and README for why. Unrelated to the release signing above:
            // this stays exactly as it was, debug builds are still never
            // signed with the real release key.
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
    // PINNED TO 3.0.0 — the first stable Coil 3.x release, not a guess
    // this time: Coil's own changelog directly confirms 3.0.0 was built
    // against Kotlin 2.0.0, matching this project's Kotlin 2.0.21 pin at
    // the language-version level that Kotlin's metadata-compatibility
    // check actually cares about (2.0.0 vs 2.0.21 are both "language
    // version 2.0" — mutually compatible; patch differences within 2.0.x
    // don't trip this check, only a 2.0→2.2 jump does).
    //
    // The full story, each step found by a real CI failure, not predicted:
    //   3.5.0 → failed checkDebugAarMetadata: needs compileSdk 36 outright.
    //   3.3.0 → passed compileSdk 36, but STILL failed checkDebugAarMetadata
    //           on compileSdk 35 (fixed by bumping this project's compileSdk
    //           34→35, safe since AGP 8.7.0 already supports it) — then
    //           failed *again*, differently: kspDebugKotlin rejected Coil's
    //           bytecode as "compiled with an incompatible version of
    //           Kotlin" (2.2.0 vs this project's 2.0.21). Both failures
    //           trace to the exact same Coil 3.3.0 changelog entry: "Update
    //           Kotlin to 2.2.0. Update Compose to 1.8.2."
    //   3.0.0 → this pin. Directly confirmed via Coil's own changelog,
    //           not inferred — the first genuinely evidence-backed choice
    //           in this saga rather than a targeted guess.
    // compileSdk stays at 35, not reverted to 34, even though 3.0.0
    // (Compose UI 1.6.8-era) likely wouldn't need it — 35 is already
    // confirmed working by a real CI run, and reverting would be an
    // unforced extra change for no benefit.
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")

    // Biometric re-auth gate on returning from background — see
    // MainActivity's FLAG_SECURE comment for the other half of this
    // (blanking the app-switcher thumbnail). 1.1.0 is the current stable
    // (non-alpha) line, confirmed directly against the official Jetpack
    // page before picking it — androidx.biometric:biometric-ktx exists
    // but is still 1.4.0-alpha as of this session, not used here for a
    // security-critical dependency. BiometricPrompt's Activity-hosted
    // constructor requires FragmentActivity specifically (confirmed
    // against AndroidX's own source, not assumed) — see MainActivity,
    // which now extends FragmentActivity instead of ComponentActivity.
    implementation("androidx.biometric:biometric:1.1.0")

    // ProcessLifecycleOwner — observes the WHOLE APP's foreground/
    // background transitions (leaving to home screen and back), not
    // internal Compose navigation, which is what the biometric gate
    // needs to trigger on. Same version as lifecycle-runtime-ktx above;
    // released in lockstep as part of the same Lifecycle library group.
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")

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
