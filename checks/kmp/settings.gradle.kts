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

        // R8 repo for R8/D8 releases
        exclusiveContent {
            forRepository {
                maven("https://storage.googleapis.com/r8-releases/raw") { name = "R8-releases" }
            }
            filter { includeModule("com.android.tools", "r8") }
        }

        // For Gradle plugins only. Last because proxies to mavenCentral.
        gradlePluginPortal()
    }
    includeBuild("../../")
}

plugins {
    id("com.gradle.enterprise") version "3.16.2"
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

rootProject.name = "check-kmp"
