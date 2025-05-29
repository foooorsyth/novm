pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
        maven {
            url = uri("https://androidx.dev/snapshots/builds/13574637/artifacts/repository")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://androidx.dev/snapshots/builds/13574637/artifacts/repository")
        }
    }
}

rootProject.name = "NoVM"
include(":sample")
include(":novm-compiler")
include(":novm-core")
include(":novm-runtime")
include(":sample-lib")
include(":sample-lib-another")
include(":novm-compose")
include(":novm-experimental")
include(":novm-experimental-plugin")
