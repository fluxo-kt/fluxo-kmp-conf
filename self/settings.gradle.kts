pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.develocity") version "3.18.2"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        gradlePluginPortal()
    }
}
