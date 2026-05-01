// Integration check for fkcSetupIdeaPlugin (IntelliJ Platform Gradle Plugin v2).
//
// IMPORTANT: This check requires downloading IntelliJ IDEA Community (~800 MB on first run).
// Run locally; do NOT add to CI without first setting up a persistent Gradle dependency cache.
//
// Usage: cd checks/intellij-platform && ./gradlew check
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
    id("io.github.fluxo-kt.fluxo-kmp-conf")
}

group = "io.github.fluxo_kt.fluxo_kmp_conf.checks"
version = libs.versions.version.get()

fkcSetupIdeaPlugin(
    config = {
        javaLangTarget = "21"
        setupVerification = true
        enableGenericAndroidLint = false
        allWarningsAsErrors = true
    },
    sinceBuild = "243",
)

dependencies {
    intellijPlatform {
        // IntelliJ IDEA Community 2024.3.x — last stable release in the 2024.3 line.
        // For 2025.3+ use `intellijIdea(version)` instead (Community no longer published separately).
        intellijIdeaCommunity("2024.3.5")
        // No bundled plugins needed for this minimal check.
    }
}
