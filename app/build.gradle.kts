plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// CI restores this keystore from repository secrets. Without it, Gradle falls back to the
// throwaway debug key, which is fine for local builds but cannot update an installed APK.
val hiveKeystore: String? = System.getenv("HIVE_KEYSTORE")

android {
    namespace = "com.hive.agents"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hive.agents"
        minSdk = 28
        targetSdk = 35
        versionCode = 27
        versionName = "2.15.1"
    }

    signingConfigs {
        if (!hiveKeystore.isNullOrBlank()) {
            create("hive") {
                storeFile = file(hiveKeystore!!)
                storePassword = System.getenv("HIVE_STORE_PASSWORD")
                keyAlias = System.getenv("HIVE_KEY_ALIAS")
                keyPassword = System.getenv("HIVE_KEY_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (!hiveKeystore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("hive")
            }
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
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20240303")
}
