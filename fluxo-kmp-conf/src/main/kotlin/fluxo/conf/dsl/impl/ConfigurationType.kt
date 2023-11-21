package fluxo.conf.dsl.impl

import org.gradle.api.Project
import setupAndroidApp
import setupAndroidLibrary
import setupGradlePlugin
import setupIdeaPlugin
import setupKotlin
import setupMultiplatform

internal enum class ConfigurationType {
    KOTLIN_MULTIPLATFORM,
    ANDROID_LIB,
    ANDROID_APP,
    KOTLIN_JVM,
    GRADLE_PLUGIN,
    IDEA_PLUGIN,
}

internal val ConfigurationType.builderMethod: String
    get() = when (this) {
        ConfigurationType.KOTLIN_MULTIPLATFORM -> Project::setupMultiplatform.name
        ConfigurationType.ANDROID_LIB -> Project::setupAndroidLibrary.name
        ConfigurationType.ANDROID_APP -> Project::setupAndroidApp.name
        ConfigurationType.KOTLIN_JVM -> Project::setupKotlin.name
        ConfigurationType.IDEA_PLUGIN -> Project::setupIdeaPlugin.name
        ConfigurationType.GRADLE_PLUGIN -> Project::setupGradlePlugin.name
    }
