plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.lib) apply false
    alias(libs.plugins.gradle.doctor) apply false
    id("io.github.fluxo-kt.fluxo-kmp-conf")
}

fkcSetupMultiplatform(
    config = {
        kotlinLangVersion = "last"
        kotlinApiVersion = "last"
        javaLangTarget = "current"
        kotlinCoreLibraries = ""
        experimentalLatestCompilation = true
        enableApiValidation = false
    },
    kmp = { allDefaultTargets() },
)
