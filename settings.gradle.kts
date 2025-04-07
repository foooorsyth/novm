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
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
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
include(":novm-compose-navigation")
