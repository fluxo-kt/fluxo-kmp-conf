plugins {
    alias(libs.plugins.kotlin.jvm)
    id("io.github.fluxo-kt.fluxo-kmp-conf")
}

group = "io.github.fluxo-kt"
version = libs.versions.version.get()

setupGradlePlugin()
