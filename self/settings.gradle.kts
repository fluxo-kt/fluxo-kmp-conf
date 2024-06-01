pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.17.4"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        gradlePluginPortal()
    }
}
