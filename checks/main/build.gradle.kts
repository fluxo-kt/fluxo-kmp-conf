plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.lib) apply false
    id("io.github.fluxo-kt.fluxo-kmp-conf")
}

setupMultiplatform(
    config = {
        kotlinLangVersion = "last"
        kotlinApiVersion = "last"
        javaLangTarget = "current"
        kotlinCoreLibraries = ""
    },
    kmp = { allDefaultTargets() },
)
