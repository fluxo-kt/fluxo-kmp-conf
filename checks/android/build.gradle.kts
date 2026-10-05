// The `pinned` bundle holds security minimums, but fluxo applies it only after Gradle has loaded
// this build's classpath. Constraints here apply while it resolves; they only raise versions.
buildscript {
    dependencies {
        constraints {
            for (dep in libs.bundles.pinned.get()) add("classpath", dep.toString())
        }
    }
}

plugins {
    // Plain `com.android.library` (NOT `com.android.kotlin.multiplatform.library`) — this check
    // exists specifically to exercise the legacy AGP `LibraryExtension`-based `setupAndroidCommon`
    // path that lost CI coverage when `checks/kmp` migrated to AGP-9 KMP+Android in 0.14.0.
    //
    // AGP 9.0 ships built-in Kotlin support (`android.builtInKotlin = true` by default), so the
    // legacy `org.jetbrains.kotlin.android` plugin is INCOMPATIBLE with this path under AGP 9
    // (`failIfIncompatiblePluginsArePresent`); do NOT apply `alias(libs.plugins.kotlin.android)`.
    alias(libs.plugins.android.lib)
    alias(libs.plugins.gradle.doctor) apply false
    id("io.github.fluxo-kt.fluxo-kmp-conf")
}

fkcSetupAndroidLibrary(
    config = {
        javaLangTarget = "current"
        setupVerification = true
        enableGenericAndroidLint = true
    },
    namespace = "io.github.fluxo_kt.fluxo_kmp_conf.checks.android",
)
