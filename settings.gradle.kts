pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.13.3"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        gradlePluginPortal()
    }
}

// Environment logging
run {
    val gradle = gradle.gradleVersion
    val java = System.getProperty("java.version")
    val cpus = Runtime.getRuntime().availableProcessors()
    logger.lifecycle("> Conf Gradle $gradle, JRE $java, $cpus CPUs")
}

rootProject.name = "fluxo-kmp-conf"

// On module update, don't forget to update '.github/workflows/deps-submission.yml'!
include(":fluxo-kmp-conf")
