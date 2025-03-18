import com.vanniktech.maven.publish.SonatypeHost
import Novmpub_gradle.Publishing

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("novmpub")
    id("com.vanniktech.maven.publish") version "0.31.0-rc2"
}

android {
    namespace = "com.forsyth.novm.android"
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

group = Publishing.GROUP
version = Publishing.VERSION

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(
        groupId = Publishing.GROUP,
        artifactId = Publishing.ARTIFACT_ID_RUNTIME,
        version = if (System.getenv("SNAPSHOT") == "SNAPSHOT") Publishing.VERSION + "-SNAPSHOT" else Publishing.VERSION
    )
    pom {
        name.set(Publishing.PROJ_NAME_RUNTIME)
        description.set(Publishing.PROJ_DESC)
        inceptionYear.set(Publishing.PROJ_INCEPTION_YEAR)
        url.set(Publishing.GITHUB_URL)
        licenses {
            license {
                name.set(Publishing.LICENSE_NAME)
                url.set(Publishing.LICENSE_URL)
                distribution.set(Publishing.LICENSE_URL)
            }
        }
        developers {
            developer {
                id.set(Publishing.DEVELOPER_USERNAME)
                name.set(Publishing.DEVELOPER_NAME)
                url.set(Publishing.DEVELOPER_URL)
            }
        }
        scm {
            url.set(Publishing.GITHUB_URL)
            connection.set(Publishing.SCM_CONNECTION)
            developerConnection.set(Publishing.SCM_DEVELOPER_CONNECTION)
        }
    }
}

dependencies {
    api(project(":novm-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}