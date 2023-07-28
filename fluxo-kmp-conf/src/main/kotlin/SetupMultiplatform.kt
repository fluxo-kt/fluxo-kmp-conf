@file:Suppress("TooManyFunctions")

import com.android.build.api.dsl.CommonExtension
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.impl.kotlin.multiplatformExtension
import fluxo.conf.impl.withType
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetsContainer
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests

public typealias MultiplatformConfigurator = KotlinMultiplatformExtension.() -> Unit

@Suppress("LongParameterList")
public fun Project.setupMultiplatform(
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    namespace: String? = null,
    setupCompose: Boolean? = null,
    enableBuildConfig: Boolean? = null,
    optIns: List<String>? = null,
    configureAndroid: (CommonExtension<*, *, *, *, *>.() -> Unit)? = null,
    body: MultiplatformConfigurator? = null,
): Unit = fluxoConfiguration {
    if (namespace != null) this.androidNamespace = namespace
    if (setupCompose != null) this.enableCompose = setupCompose
    if (enableBuildConfig != null) this.enableBuildConfig = enableBuildConfig
    if (!optIns.isNullOrEmpty()) this.optIns += optIns
    config?.invoke(this)

    configureAsMultiplatform {
        if (body != null) {
            kotlinMultiplatform(body)
        }

        if (configureAndroid != null) {
            onAndroidTarget {
                onAndroidExtension(configureAndroid)
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
    targets.withType<KotlinNativeTargetWithTests<*>>().all {
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


// FIXME: Move to BCV setup
@Deprecated("For deletion")
internal enum class Target {
    ANDROID,
    JVM,
    JS,
}

// FIXME: Move to BCV setup
@Suppress("DEPRECATION")
@Deprecated("For deletion")
internal fun Project.isMultiplatformTargetEnabled(target: Target): Boolean =
    multiplatformExtension.isMultiplatformTargetEnabled(target)

@Suppress("DEPRECATION")
private fun KotlinTargetsContainer.isMultiplatformTargetEnabled(target: Target): Boolean {
    return targets.any {
        when (it.platformType) {
            KotlinPlatformType.androidJvm -> target == Target.ANDROID
            KotlinPlatformType.jvm -> target == Target.JVM
            KotlinPlatformType.js -> target == Target.JS
            KotlinPlatformType.common,
            KotlinPlatformType.native,
            KotlinPlatformType.wasm,
            -> false
        }
    }
}


// region Old darwin compat helpers

/**
 *
 * @see KotlinTargetContainerWithNativeShortcuts.ios
 * @see KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
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
 * @see KotlinTargetContainerWithNativeShortcuts.watchos
 * @see KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
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
 * @see KotlinTargetContainerWithNativeShortcuts.tvos
 * @see KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
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
 * @see KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
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
