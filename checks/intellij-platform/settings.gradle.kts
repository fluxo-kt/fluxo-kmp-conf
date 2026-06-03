import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    repositories {
        // For Gradle plugins only. Last because proxies to mavenCentral.
        gradlePluginPortal()
    }
    includeBuild("../../")
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.16.0"
    id("com.gradle.develocity") version "4.4.2"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
        intellijPlatform { defaultRepositories() }
    }
}

rootProject.name = "check-intellij-platform"
