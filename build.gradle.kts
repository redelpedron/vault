// Root build file. Plugin *versions* are declared here (with apply false)
// and actually applied in app/build.gradle.kts — this keeps a single
// source of truth for versions across (a future) multi-module project,
// matching the same "one rule definition" principle the Go backend uses
// for ID validation (see internal/domain/models/id.go).
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
