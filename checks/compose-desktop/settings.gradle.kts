pluginManagement {
    repositories {
        // Google/Firebase/GMS/Androidx libraries
        // Don't use exclusiveContent for androidx libraries so that snapshots work.
        google {
            content {
                includeGroupByRegex("android.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("org\\.chromium.*")
            }
        }
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

rootProject.name = "check-compose-desktop"
