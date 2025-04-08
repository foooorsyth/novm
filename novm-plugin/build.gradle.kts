plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    id("java-gradle-plugin")
    id("maven-publish")
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

group = "com.forsyth.novm"
version = "1.0.0"

gradlePlugin {
    plugins {
        create("novm-plugin") {
            id = "com.forsyth.novm.novm-plugin"
            implementationClass = "com.forsyth.novm.plugin.NoVMPlugin"
        }
    }
}


publishing {
    repositories {
        mavenLocal()
    }
}

dependencies {
    implementation(gradleApi())
    implementation(libs.asm)
}
