plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "net.nicochristmann.p2pgames"
    compileSdk = 34

    defaultConfig {
        applicationId = "net.nicochristmann.p2pgames"
        minSdk = 24
        targetSdk = 34
        // On CI the run number is used, so every build that could go to the
        // Play Store has a strictly increasing versionCode automatically.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2
        versionName = "1.1"
    }

    // Release signing comes from the environment (see .github/workflows/
    // android.yml and README "Releasing to the Play Store"). Without a
    // keystore the release bundle is built unsigned.
    val keystorePath = System.getenv("KEYSTORE_FILE")
    if (keystorePath != null && file(keystorePath).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                val storePass = System.getenv("KEYSTORE_PASSWORD")
                storePassword = storePass
                keyAlias = System.getenv("KEY_ALIAS")?.ifBlank { null } ?: "upload"
                // keytool defaults the key password to the keystore password
                // (PKCS12 keystores always share it), so fall back to that
                // when no separate KEY_PASSWORD is configured.
                keyPassword = System.getenv("KEY_PASSWORD")?.ifBlank { null } ?: storePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
}
