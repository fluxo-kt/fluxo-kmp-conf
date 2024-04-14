package fluxo.conf.dsl.impl

import fkcSetupAndroidApp
import fkcSetupAndroidLibrary
import fkcSetupGradlePlugin
import fkcSetupIdeaPlugin
import fkcSetupKotlin
import fkcSetupMultiplatform
import org.gradle.api.Project

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
        ConfigurationType.KOTLIN_MULTIPLATFORM -> Project::fkcSetupMultiplatform.name
        ConfigurationType.ANDROID_LIB -> Project::fkcSetupAndroidLibrary.name
        ConfigurationType.ANDROID_APP -> Project::fkcSetupAndroidApp.name
        ConfigurationType.KOTLIN_JVM -> Project::fkcSetupKotlin.name
        ConfigurationType.IDEA_PLUGIN -> Project::fkcSetupIdeaPlugin.name
        ConfigurationType.GRADLE_PLUGIN -> Project::fkcSetupGradlePlugin.name
    }
