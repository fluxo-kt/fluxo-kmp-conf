plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.gradle.plugin.publish) apply false
    alias(libs.plugins.kotlinx.binCompatValidator) apply false
    id("io.github.fluxo-kt.fluxo-kmp-conf")
}

group = "io.github.fluxo-kt"
version = libs.versions.version.get()

setupGradlePlugin()
