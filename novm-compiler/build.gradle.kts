plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    id("novmpub")
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

group = Novmpub_gradle.Publishing.GROUP
version = Novmpub_gradle.Publishing.VERSION

dependencies {
    implementation(libs.symbol.processing.api)
    implementation(project(":novm-core"))
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
