pluginManagement {
    repositories {
        google()
        // For Gradle plugins only. Last because proxies to mavenCentral.
        gradlePluginPortal()
    }
    includeBuild("../../")
}

plugins {
    id("com.gradle.develocity") version "3.18.2"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
    }
}

rootProject.name = "check-gradle-plugin"
