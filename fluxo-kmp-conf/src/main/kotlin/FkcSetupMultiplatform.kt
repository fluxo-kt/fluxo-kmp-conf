@file:Suppress("LongParameterList", "MaxLineLength", "DuplicatedCode")
@file:JvmName("Fkc")
@file:JvmMultifileClass

import com.android.build.api.dsl.CommonExtension
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.container.KmpConfigurationContainerDsl
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.impl.withType
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests

public typealias AndroidCommonExtension = CommonExtension<*, *, *, *, *, *>

public typealias MultiplatformConfigurator = KotlinMultiplatformExtension.() -> Unit

/**
 * Lazily configures a Kotlin Multiplatform module (Gradle [Project]).
 *
 * @receiver The [Project] to configure.
 *
 * @param config Configuration block for the [FluxoConfigurationExtension].
 *
 * @param namespace The Android namespace to use for the project.
 * @param enableBuildConfig Whether to enable the BuildConfig generation.
 * @param setupCompose Whether to set up Compose in this module (auto-detected if already applied).
 * @param optIns List of the Kotlin opt-ins to add in the project.
 *
 * @param android Configuration block for the Android target [AndroidCommonExtension].
 * @param kmp Configuration block for the lazy KMP targets [KmpConfigurationContainerDsl].
 * @param kotlin Configuration block for the [KotlinMultiplatformExtension].
 *
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.androidNamespace
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.enableBuildConfig
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.enableCompose
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.optIns
 */
@JvmName("setupMultiplatform")
public fun Project.fkcSetupMultiplatform(
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    namespace: String? = null,
    enableBuildConfig: Boolean? = null,
    setupCompose: Boolean? = null,
    optIns: List<String>? = null,
    android: (AndroidCommonExtension.() -> Unit)? = null,
    kmp: (KmpConfigurationContainerDsl.() -> Unit)? = null,
    kotlin: (KotlinMultiplatformExtension.() -> Unit)? = null,
) {
    val project = this
    project.fluxoConfiguration c@{
        namespace?.let { this.androidNamespace = it }
        setupCompose?.let { this.enableCompose = it }
        enableBuildConfig?.let { this.enableBuildConfig = it }

        if (!optIns.isNullOrEmpty()) {
            this.optIns += optIns
        }

        config?.invoke(this)

        if (kmp == null && kotlin == null && android == null) {
            return@c
        }

        asKmp {
            kmp?.invoke(this)

            kotlin?.let { kotlinMultiplatform(it) }

            if (android != null) {
                onAndroidTarget {
                    onAndroidExtension(android)
                }
            }
        }
    }
}


/**
 * Configure a separate Kotlin/Native tests where code runs in worker thread.
 */
public fun KotlinMultiplatformExtension.setupBackgroundNativeTests() {
    // Configure a separate test where code runs in worker thread
    // https://kotlinlang.org/docs/compiler-reference.html#generate-worker-test-runner-trw.
    targets.withType<KotlinNativeTargetWithTests<*>> {
        val background = "background"
        binaries {
            test(background, listOf(DEBUG)) {
                freeCompilerArgs += "-trw"
            }
        }
        testRuns.create(background) {
            setExecutionSourceFrom(binaries.getTest(background, DEBUG))
        }
    }
}


// region Old darwin compat helpers

/**
 *
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.ios
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
 */
public fun KotlinMultiplatformExtension.iosCompat(
    x64: String? = DEFAULT_TARGET_NAME,
    arm64: String? = DEFAULT_TARGET_NAME,
    simulatorArm64: String? = DEFAULT_TARGET_NAME,
) {
    enableTarget(name = x64, enableDefault = { iosX64() }, enableNamed = { iosX64(it) })
    enableTarget(name = arm64, enableDefault = { iosArm64() }, enableNamed = { iosArm64(it) })
    enableTarget(
        name = simulatorArm64,
        enableDefault = { iosSimulatorArm64() },
        enableNamed = { iosSimulatorArm64(it) },
    )
}

/**
 *
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.watchos
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
 */
public fun KotlinMultiplatformExtension.watchosCompat(
    x64: String? = DEFAULT_TARGET_NAME,
    arm32: String? = DEFAULT_TARGET_NAME,
    arm64: String? = DEFAULT_TARGET_NAME,
    simulatorArm64: String? = DEFAULT_TARGET_NAME,
) {
    enableTarget(name = x64, enableDefault = { watchosX64() }, enableNamed = { watchosX64(it) })
    enableTarget(
        name = arm32,
        enableDefault = { watchosArm32() },
        enableNamed = { watchosArm32(it) },
    )
    enableTarget(
        name = arm64,
        enableDefault = { watchosArm64() },
        enableNamed = { watchosArm64(it) },
    )
    enableTarget(
        name = simulatorArm64,
        enableDefault = { watchosSimulatorArm64() },
        enableNamed = { watchosSimulatorArm64(it) },
    )
}

/**
 *
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.tvos
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
 */
public fun KotlinMultiplatformExtension.tvosCompat(
    x64: String? = DEFAULT_TARGET_NAME,
    arm64: String? = DEFAULT_TARGET_NAME,
    simulatorArm64: String? = DEFAULT_TARGET_NAME,
) {
    enableTarget(name = x64, enableDefault = { tvosX64() }, enableNamed = { tvosX64(it) })
    enableTarget(name = arm64, enableDefault = { tvosArm64() }, enableNamed = { tvosArm64(it) })
    enableTarget(
        name = simulatorArm64,
        enableDefault = { tvosSimulatorArm64() },
        enableNamed = { tvosSimulatorArm64(it) },
    )
}

/**
 *
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
 */
public fun KotlinMultiplatformExtension.macosCompat(
    x64: String? = DEFAULT_TARGET_NAME,
    arm64: String? = DEFAULT_TARGET_NAME,
) {
    enableTarget(name = x64, enableDefault = { macosX64() }, enableNamed = { macosX64(it) })
    enableTarget(name = arm64, enableDefault = { macosArm64() }, enableNamed = { macosArm64(it) })
}

private fun KotlinMultiplatformExtension.enableTarget(
    name: String?,
    enableDefault: KotlinMultiplatformExtension.() -> Unit,
    enableNamed: KotlinMultiplatformExtension.(String) -> Unit,
) {
    if (name != null) {
        if (name == DEFAULT_TARGET_NAME) {
            enableDefault()
        } else {
            enableNamed(name)
        }
    }
}

private const val DEFAULT_TARGET_NAME = ".DEFAULT_TARGET_NAME"

// endregion
