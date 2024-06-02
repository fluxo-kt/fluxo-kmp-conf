plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.sam.receiver) apply false
    alias(libs.plugins.kotlinx.binCompatValidator) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.deps.guard) apply false
    alias(libs.plugins.gradle.doctor) apply false
    alias(libs.plugins.gradle.plugin.publish) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.fluxo.conf)
}
