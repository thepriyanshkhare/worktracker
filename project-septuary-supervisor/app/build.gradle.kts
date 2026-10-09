plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.septuary.supervisor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.septuary.supervisor"
        minSdk = 26
        targetSdk = 34
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = 100 + run
        versionName = "2.0.$run"
    }

    // Same stable signing key as the main app, supplied by CI from repository secrets.
    val ksPath = System.getenv("SEPTUARY_KEYSTORE_PATH")
    val ksPass = System.getenv("SEPTUARY_KEYSTORE_PASSWORD")
    val hasStableKey = !ksPath.isNullOrBlank() && !ksPass.isNullOrBlank() && file(ksPath).exists()
    signingConfigs {
        if (hasStableKey) {
            create("stable") {
                storeFile = file(ksPath!!)
                storePassword = ksPass
                keyAlias = "septuary"
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        debug {
            if (hasStableKey) signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // Read-only: this app only ever listens to Firestore, never writes.
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Anonymous auth required by firestore.rules (Oct 2026 hardening) — the Supervisor
    // app must sign in before it can read either document.
    implementation("com.google.firebase:firebase-auth-ktx")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
