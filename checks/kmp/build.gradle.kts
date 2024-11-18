import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.lib)
    alias(libs.plugins.gradle.doctor) apply false
    alias(libs.plugins.vanniktech.mvn.publish)
    id("io.github.fluxo-kt.fluxo-kmp-conf")
}

val manual = true

fkcSetupMultiplatform(
    config = {
        kotlinLangVersion = "last"
        kotlinApiVersion = "last"
        javaLangTarget = "current"
        kotlinCoreLibraries = ""
        experimentalLatestCompilation = true
        latestSettingsForTests = true
        setupVerification = true
        enableGenericAndroidLint = true
        enableGradleDoctor = true
        enablePublication = true
    },
    kmp = { if (!manual) allDefaultTargets() },
) {
    if (manual) {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        applyDefaultHierarchyTemplate(KotlinHierarchyTemplate.fluxoKmpConf)

        jvm()
        androidTarget()

        js {
            browser()
            nodejs()
        }
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser()
            nodejs()
            d8()
        }

        iosX64()
        iosArm64()
        iosSimulatorArm64()

        watchosArm64()
        watchosArm32()
        watchosX64()
        watchosSimulatorArm64()

        tvosArm64()
        tvosX64()
        tvosSimulatorArm64()

        macosArm64()
        macosX64()

        linuxX64()
        linuxArm64()

        mingwX64()
    }
}
