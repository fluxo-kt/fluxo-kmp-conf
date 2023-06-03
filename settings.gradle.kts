pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.13.3"
}

dependencyResolutionManagement {
//    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev/")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

logger.lifecycle("> Conf Gradle version is ${gradle.gradleVersion}")
logger.lifecycle("> Conf JRE version is ${System.getProperty("java.version")}")
logger.lifecycle("> Conf CPUs ${Runtime.getRuntime().availableProcessors()}")

rootProject.name = "fluxo-kmp-conf"

// On module update, don't forget to update '.github/workflows/deps-submission.yml'!

include(":fluxo-kmp-conf")
