import com.vanniktech.maven.publish.SonatypeHost
import Novmpub_gradle.Publishing

plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    id("novmpub")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.31.0-rc2"
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

group = Publishing.GROUP
version = Publishing.VERSION

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(
        groupId = Publishing.GROUP,
        artifactId = Publishing.ARTIFACT_ID_COMPILER,
        version = if (System.getenv("SNAPSHOT") != null || System.getenv("SNAPSHOT") != "") Publishing.VERSION + "-SNAPSHOT" else Publishing.VERSION
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
    implementation(libs.symbol.processing.api)
    implementation(project(":novm-core"))
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
