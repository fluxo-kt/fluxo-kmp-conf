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
import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
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

        if (holder.kotlinPluginVersion >= KOTLIN_1_8_20) {
            @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
            wasmJs()
        }

        kotlinMultiplatform {
            setupBackgroundNativeTests()
        }
    }
}
