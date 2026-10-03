plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.septuary.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.septuary.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // No auto-backup of app data — the encrypted DB should never leave this device,
    // not even via Google's Auto Backup to Drive. See AndroidManifest (allowBackup=false).
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Encrypted local database: Room (schema/queries) over SQLCipher (encryption at rest).
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Cloud sync: pushes today's status to Firestore for the parents' supervisor app to
    // read — a tri-state summary per section (Medicine, Food, Exercise), plus an explicit,
    // approved detail doc (medicine/food names, exercise detail, recent weight). Glucose and
    // free-text notes are never synced — see SyncRepository.kt.
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Anonymous auth required by firestore.rules (Oct 2026 hardening) — blocks any
    // unauthenticated client from reading/writing, even if the project is discovered.
    implementation("com.google.firebase:firebase-auth-ktx")

    // Health Connect: read-only Weight + Steps auto-sync. See HealthConnectRepository.kt.
    implementation("androidx.health.connect:connect-client:1.2.0-alpha06")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
