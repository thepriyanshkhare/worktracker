// Top-level build file
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // The Compose compiler Gradle plugin only exists for Kotlin 2.0+ — it replaces the old
    // composeOptions{kotlinCompilerExtensionVersion} mechanism used pre-Kotlin-2.0.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    // Reads google-services.json and wires the Firebase project into the app at build time.
    // Only this app (the patient-facing one) pushes status; see SyncRepository.kt.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
