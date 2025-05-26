plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // region nav3plugins
    alias(libs.plugins.jetbrains.kotlin.serialization)
    // endregion nav3plugins
}

android {
    namespace = "com.forsyth.novm"
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))

    // region nav3libs
//    implementation(libs.androidx.navigation3.runtime)
//    implementation(libs.androidx.navigation3.ui)
//    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
//    implementation(libs.androidx.material3)
//    implementation(libs.androidx.material3.navigation3)
//    implementation(libs.kotlinx.serialization.core)
//    implementation(libs.kotlinx.serialization.json)
    // endregion nav3libs

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}