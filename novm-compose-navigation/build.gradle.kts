import com.vanniktech.maven.publish.SonatypeHost
import Novmpub_gradle.Publishing

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("novmpub")
    id("com.vanniktech.maven.publish") version "0.31.0-rc2"
}

android {
    namespace = "com.forsyth.novm.compose.navigation"
    compileSdk = 35

    defaultConfig {
        minSdk = 16

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":novm-core"))
    implementation(project(":novm-runtime"))
    implementation(project(":novm-compose"))
    implementation(libs.androidx.navigation.compose)
    // region COMPOSE
    // TODO just use foundation compose deps
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    //implementation(libs.material) // TODO investigate
    implementation(libs.androidx.activity.compose.v1100)
    // endregion COMPOSE
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}