package fluxo.conf.dsl.container

import fluxo.conf.dsl.container.target.AndroidNativeTarget
import fluxo.conf.dsl.container.target.AndroidTarget
import fluxo.conf.dsl.container.target.AppleIosTarget
import fluxo.conf.dsl.container.target.AppleMacosTarget
import fluxo.conf.dsl.container.target.AppleTvosTarget
import fluxo.conf.dsl.container.target.AppleWatchosTarget
import fluxo.conf.dsl.container.target.JsTarget
import fluxo.conf.dsl.container.target.JvmTarget
import fluxo.conf.dsl.container.target.LinuxTarget
import fluxo.conf.dsl.container.target.MingwTarget
import fluxo.conf.dsl.container.target.WasmNativeTarget
import fluxo.conf.dsl.container.target.WasmTarget
import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

public interface KmpConfigurationContainerDsl :
    JvmTarget.Configure,
    AndroidTarget.Configure,
    JsTarget.Configure,
    AppleIosTarget.Configure,
    AppleMacosTarget.Configure,
    AppleTvosTarget.Configure,
    AppleWatchosTarget.Configure,
    LinuxTarget.Configure,
    MingwTarget.Configure,
    WasmTarget.Configure,
    AndroidNativeTarget.Configure,
    WasmNativeTarget.Configure {

    /**
     * Executes the given [action] for all [KotlinTarget]s of the given [type].
     */
    public fun <T : KotlinTargetContainer<KotlinTarget>> onTarget(
        type: Class<T>,
        action: Action<in T>,
    )

    /**
     * Executes the given [action] for all [AndroidTarget]s.
     */
    public fun onAndroidTarget(action: Action<in AndroidTarget<*>>): Unit =
        onTarget(AndroidTarget::class.java, action)


    /**
     * Executes the given [action] for the Kotlin module with any target enabled.
     * [action] can apply plugins via [Container.applyPlugins] and configure common settings.
     */
    public fun common(action: Container.() -> Unit)

    /**
     * Executes the given [action] for the Kotlin module with any target enabled.
     */
    public fun kotlin(action: KotlinProjectExtension.() -> Unit)

    /**
     * Executes the given [action] for the KMP module with any target enabled.
     */
    public fun kotlinMultiplatform(action: KotlinMultiplatformExtension.() -> Unit)


    /**
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension.targetHierarchy
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetHierarchyDsl.default
     */
    public fun allDefaultTargets()
}
