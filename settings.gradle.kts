pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.16.1"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        gradlePluginPortal()
    }
}

rootProject.name = "fluxo-kmp-conf"

// On module update, don't forget to update '.github/workflows/deps-submission.yml'!
include(":fluxo-kmp-conf")
project(":fluxo-kmp-conf").name = "plugin"
