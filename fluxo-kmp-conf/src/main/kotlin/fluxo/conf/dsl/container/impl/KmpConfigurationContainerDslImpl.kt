package fluxo.conf.dsl.container.impl

import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.container.KmpConfigurationContainerDsl
import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.dsl.container.impl.custom.CommonActionContainer
import fluxo.conf.dsl.container.impl.custom.KotlinMultiplatformActionContainer
import fluxo.conf.dsl.container.impl.custom.KotlinProjectActionContainer
import fluxo.conf.dsl.container.impl.target.TargetAndroidContainer
import fluxo.conf.dsl.container.impl.target.TargetAndroidNativeContainer
import fluxo.conf.dsl.container.impl.target.TargetAppleIosContainer
import fluxo.conf.dsl.container.impl.target.TargetAppleMacosContainer
import fluxo.conf.dsl.container.impl.target.TargetAppleTvosContainer
import fluxo.conf.dsl.container.impl.target.TargetAppleWatchosContainer
import fluxo.conf.dsl.container.impl.target.TargetJsContainer
import fluxo.conf.dsl.container.impl.target.TargetJvmContainer
import fluxo.conf.dsl.container.impl.target.TargetLinuxContainer
import fluxo.conf.dsl.container.impl.target.TargetMingwContainer
import fluxo.conf.dsl.container.impl.target.TargetWasmContainer
import fluxo.conf.dsl.container.impl.target.TargetWasmNativeContainer
import fluxo.conf.impl.kotlin.KOTLIN_1_8_20
import fluxo.conf.impl.kotlin.KOTLIN_1_9_20
import fluxo.conf.impl.kotlin.KOTLIN_2_0
import fluxoKmpConf
import org.gradle.api.Action
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import setupBackgroundNativeTests

internal class KmpConfigurationContainerDslImpl(
    override val holder: ContainerHolder,
) : KmpConfigurationContainerDsl,
    TargetJvmContainer.Configure,
    TargetAndroidContainer.Configure,
    TargetJsContainer.Configure,
    TargetAppleIosContainer.Configure,
    TargetAppleMacosContainer.Configure,
    TargetAppleTvosContainer.Configure,
    TargetAppleWatchosContainer.Configure,
    TargetLinuxContainer.Configure,
    TargetMingwContainer.Configure,
    TargetWasmContainer.Configure,
    TargetAndroidNativeContainer.Configure,
    TargetWasmNativeContainer.Configure {

    override fun <T : KotlinTargetContainer<KotlinTarget>> onTarget(
        type: Class<T>,
        action: Action<in T>,
    ) {
        holder.containers.withType(type, action)
    }


    override fun common(action: Container.() -> Unit) {
        holder.configureCustom(
            CommonActionContainer.NAME, ::CommonActionContainer, action,
        )
    }

    override fun kotlin(action: KotlinProjectExtension.() -> Unit) {
        holder.configureCustom(
            KotlinProjectActionContainer.NAME, ::KotlinProjectActionContainer, action,
        )
    }

    override fun kotlinMultiplatform(action: KotlinMultiplatformExtension.() -> Unit) {
        holder.configureCustom(
            KotlinMultiplatformActionContainer.NAME, ::KotlinMultiplatformActionContainer, action,
        )
    }

    /**
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension.targetHierarchy
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetHierarchyDsl.default
     */
    override fun allDefaultTargets() {
        // KotlinMultiplatformExtension.targetHierarchy
        // https://kotlinlang.org/docs/whatsnew1820.html#new-approach-to-source-set-hierarchy

        jvm()
        androidLibrary()
        js()

        ios()
        watchos()
        tvos()
        macos()

        linux()
        mingw()

        // WASM target has a problem with Gradle 8+
        // Reason: One task uses the output of another task
        //  without declaring an explicit or implicit dependency.
        // Fixed in Kotlin 2.0.
        val kotlinPluginVersion = holder.kotlinPluginVersion
        @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
        if (kotlinPluginVersion >= KOTLIN_1_8_20 &&
            (kotlinPluginVersion >= KOTLIN_2_0 || isGradleNotFailingOnImplicitTaskDependencies())
        ) {
            wasmJs()

            // Usage of both Wasi and JS targets lead to problems atm.
            if (ENABLE_WASM_WASI && kotlinPluginVersion >= KOTLIN_1_9_20) {
                wasmWasi()
            }
        }

        kotlinMultiplatform {
            if (kotlinPluginVersion >= KOTLIN_1_9_20) {
                // Apply the extended default hierarchy explicitly (needed after 1.9.20-RC).
                // It'll create, for example, the iosMain source set.
                // https://kotlinlang.org/docs/whatsnew1920.html#set-up-the-target-hierarchy
                @OptIn(ExperimentalKotlinGradlePluginApi::class)
                applyDefaultHierarchyTemplate(KotlinHierarchyTemplate.fluxoKmpConf)
            }

            setupBackgroundNativeTests()
        }
    }

    // https://docs.gradle.org/current/userguide/validation_problems.html#implicit_dependency
    private fun isGradleNotFailingOnImplicitTaskDependencies() =
        GradleVersion.version(holder.project.gradle.gradleVersion) < GradleVersion.version("8.0")
}

private const val ENABLE_WASM_WASI = false
