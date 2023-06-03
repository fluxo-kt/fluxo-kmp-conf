pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev/")
    }
}

rootProject.name = "fluxo-kmp-conf"

// On module update, don't forget to update '.github/workflows/deps-submission.yml'!

include(":fluxo-kmp-conf")
