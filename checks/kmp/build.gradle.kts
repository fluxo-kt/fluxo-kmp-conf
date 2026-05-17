import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.targets.web.yarn.BaseYarnRootExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // AGP 9.0+ rejects `com.android.library` + `kotlin("multiplatform")` co-application
    // (see https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html).
    // The KMP-aware plugin auto-creates the `android` KMP target via the `kotlin { android { } }`
    // DSL block, replacing the legacy `androidTarget()` call below.
    alias(libs.plugins.android.kotlinMultiplatformLib)
    alias(libs.plugins.gradle.doctor) apply false
    alias(libs.plugins.vanniktech.mvn.publish)
    id("io.github.fluxo-kt.fluxo-kmp-conf")
}

val manual = false

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
        // Verifies Fluxo's auto-config of `kotlin.android { }` from FluxoConfigurationExtension.
        androidNamespace = "io.github.fluxo_kt.fluxo_kmp_conf.checks.kmp"
        androidCompileSdk = 34
        androidMinSdk = 24
    },
    kmp = { if (!manual) allDefaultTargets() },
) {
    if (manual) {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        applyDefaultHierarchyTemplate(KotlinHierarchyTemplate.fluxoKmpConf)

        jvm()
        // The `android { }` block (provided by `com.android.kotlin.multiplatform.library`)
        // creates the android KMP target. No explicit `androidTarget()` — AGP 9 hard-rejects it.
        // namespace/compileSdk/minSdk are auto-applied by Fluxo from `androidNamespace` etc.
        @Suppress("UnstableApiUsage")
        android {}

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

// Mocha's current transitive ranges still admit vulnerable versions; keep the
// fixture lockfile on patched versions until Mocha moves its own ranges.
listOf("kotlinYarn", "kotlinWasmYarn").forEach { extensionName ->
    extensions.findByName(extensionName)?.let { extension ->
        (extension as BaseYarnRootExtension).apply {
            resolution("diff", "8.0.3")
            resolution("js-yaml", "4.1.1")
            resolution("serialize-javascript", "7.0.5")
        }
    }
}
