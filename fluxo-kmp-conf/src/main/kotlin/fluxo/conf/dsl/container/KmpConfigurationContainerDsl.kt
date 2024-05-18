package fluxo.conf.dsl.container

import fluxo.conf.dsl.container.target.AndroidNativeTarget
import fluxo.conf.dsl.container.target.AppleIosTarget
import fluxo.conf.dsl.container.target.AppleMacosTarget
import fluxo.conf.dsl.container.target.AppleTvosTarget
import fluxo.conf.dsl.container.target.AppleWatchosTarget
import fluxo.conf.dsl.container.target.JsTarget
import fluxo.conf.dsl.container.target.LinuxTarget
import fluxo.conf.dsl.container.target.MingwTarget
import fluxo.conf.dsl.container.target.WasmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

public interface KmpConfigurationContainerDsl :
    KotlinConfigurationContainerDsl<KotlinProjectExtension>,
    JsTarget.Configure,
    AppleIosTarget.Configure,
    AppleMacosTarget.Configure,
    AppleTvosTarget.Configure,
    AppleWatchosTarget.Configure,
    LinuxTarget.Configure,
    MingwTarget.Configure,
    WasmTarget.Configure,
    AndroidNativeTarget.Configure {

    /**
     * Executes the given [action] for the KMP module with any target enabled.
     */
    public fun kotlinMultiplatform(action: KotlinMultiplatformExtension.() -> Unit)


    /**
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension.targetHierarchy
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetHierarchyDsl.default
     */
    public fun allDefaultTargets(wasmWasi: Boolean = ENABLE_WASM_WASI)
}

private const val ENABLE_WASM_WASI = false
