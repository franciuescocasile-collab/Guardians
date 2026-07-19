plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.guardians.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guardians.app"
        minSdk = 26
        targetSdk = 35
        // Versionamento semantico (Major.Minor.Patch): la Minor cresce a ogni
        // ciclo di funzioni nuove, la Patch per le sole correzioni.
        versionCode = 31
        versionName = "3.16.0"
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Health Connect: legge i dati di sonno che Samsung Health condivide con la
    // piattaforma di salute di Android (il "magazzino comune" dei dati sanitari).
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")
}
