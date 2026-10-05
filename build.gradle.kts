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
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.sam.receiver) apply false
    alias(libs.plugins.kotlinx.binCompatValidator) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.deps.guard) apply false
    alias(libs.plugins.gradle.doctor) apply false
    alias(libs.plugins.gradle.plugin.publish) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.vanniktech.mvn.publish) apply false
    alias(libs.plugins.fluxo.conf)
}
