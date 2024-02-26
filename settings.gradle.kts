pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

// TODO: Limit the repositories in packages (check all declarations in project!)
//  https://github.com/android-password-store/Android-Password-Store/blob/36f93cd60903dfbb86536ee7a16575afc782567a/settings.gradle.kts#L10
//  https://github.com/ZacSweers/CatchUp/blob/f1cb33f62e5f8b49825ac27f89a954f7f23f41ac/settings.gradle.kts#L92

plugins {
    id("com.gradle.enterprise") version "3.16.2"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        gradlePluginPortal()
    }
}

rootProject.name = "fluxo-kmp-conf"

":fluxo-kmp-conf".let {
    include(it)
    project(it).name = "plugin"
}
