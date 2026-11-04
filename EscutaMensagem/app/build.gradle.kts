plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.escutamensagem"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "br.com.escutamensagem"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
