import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    repositories {
        // For Gradle plugins only. Last because proxies to mavenCentral.
        gradlePluginPortal()
    }
    includeBuild("../../")
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.19.0"
    id("com.gradle.develocity") version "4.6.0"
    // fluxo's settings part, from the included root build (no version needed).
    id("io.github.fluxo-kt.fluxo-kmp-conf.settings")
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
