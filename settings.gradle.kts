@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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

    // region Self-apply the plugin to itself immediately with the included build.
    // `buildSrc` is not used to avoid caching and configuration issues.
    // References:
    // - https://github.com/hakanai/self-applying-gradle-plugin
    // - https://gist.github.com/johnrengelman/9a20697b2246a9bfaca2
    // - https://discuss.gradle.org/t/in-a-gradle-plugin-project-how-can-i-apply-the-plugin-itself-in-the-build/5700
    // - https://docs.gradle.org/8.6/userguide/sharing_build_logic_between_subprojects.html#sec:using_buildsrc
    includeBuild("self")
    // endregion
}

// TODO: Limit the repositories in packages (check all declarations in project!)
//  https://github.com/android-password-store/Android-Password-Store/blob/36f93cd60903dfbb86536ee7a16575afc782567a/settings.gradle.kts#L10
//  https://github.com/ZacSweers/CatchUp/blob/f1cb33f62e5f8b49825ac27f89a954f7f23f41ac/settings.gradle.kts#L92

plugins {
    id("com.gradle.develocity") version "3.18.2"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
    }
}

rootProject.name = "fluxo-kmp-conf"

":fluxo-kmp-conf".let {
    include(it)
    // NOTE: Name is used in the `jitpack.yml`!
    project(it).name = "plugin"
}
