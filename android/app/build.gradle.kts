plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.bountyradar.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bountyradar.app"
        minSdk = 26          // Android 8.0 — covers ~95% of devices, needed for notif channels
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    // Firebase — BoM keeps versions aligned. Spark (free) plan covers all of these.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")

    // Google sign-in (one of the login options)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Jetpack Compose UI
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Open program links in a browser tab
    implementation("androidx.browser:browser:1.8.0")

    implementation("androidx.core:core-ktx:1.13.1")

    // Provides the Material3 XML theme (Theme.Material3.*) used by the app's
    // host theme in res/values/themes.xml. Compose's material3 does NOT include it.
    implementation("com.google.android.material:material:1.12.0")

    // Persist theme choice + bookmarks across launches.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // System bars / edge-to-edge insets helpers.
    implementation("androidx.core:core-splashscreen:1.0.1")
}
