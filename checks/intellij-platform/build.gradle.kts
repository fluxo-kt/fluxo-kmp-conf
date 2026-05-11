// Integration check for fkcSetupIdeaPlugin (IntelliJ Platform Gradle Plugin v2).
//
// IMPORTANT: This check requires downloading IntelliJ IDEA Community (~800 MB on first run).
// Run locally; do NOT add to CI without first setting up a persistent Gradle dependency cache.
//
// Usage: cd checks/intellij-platform && ./gradlew check
plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.intellij.platform")
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
        // IntelliJ IDEA 2024.3.x keeps this check on the plugin's documented since-build line.
        intellijIdea("2024.3.6")
        // No bundled plugins needed for this minimal check.
    }
}
