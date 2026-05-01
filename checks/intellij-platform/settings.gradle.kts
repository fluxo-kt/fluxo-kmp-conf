pluginManagement {
    repositories {
        // For Gradle plugins only. Last because proxies to mavenCentral.
        gradlePluginPortal()
        // IntelliJ Platform Gradle Plugin v2 is also published here.
        maven("https://packages.jetbrains.com/repositories/public")
    }
    includeBuild("../../")
}

plugins {
    id("com.gradle.develocity") version "3.18.2"
}

dependencyResolutionManagement {
    repositories {
        // For IntelliJ IDEA Community and bundled plugin artifacts.
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://packages.jetbrains.com/repositories/public")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")
        // For Marketplace plugins.
        maven("https://plugins.jetbrains.com/maven")
        mavenCentral()
    }
}

rootProject.name = "check-intellij-platform"
