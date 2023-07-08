package fluxo.conf.dsl.container

import fluxo.conf.dsl.container.target.TargetAndroid
import fluxo.conf.dsl.container.target.TargetAndroidNative
import fluxo.conf.dsl.container.target.TargetAppleIos
import fluxo.conf.dsl.container.target.TargetAppleMacos
import fluxo.conf.dsl.container.target.TargetAppleTvos
import fluxo.conf.dsl.container.target.TargetAppleWatchos
import fluxo.conf.dsl.container.target.TargetJs
import fluxo.conf.dsl.container.target.TargetJvm
import fluxo.conf.dsl.container.target.TargetLinux
import fluxo.conf.dsl.container.target.TargetMingw
import fluxo.conf.dsl.container.target.TargetWasm
import fluxo.conf.dsl.container.target.TargetWasmNative
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

public interface KmpConfigurationContainerDsl :
    TargetJvm.Configure,
    TargetAndroid.Configure,
    TargetJs.Configure,
    TargetAppleIos.Configure,
    TargetAppleMacos.Configure,
    TargetAppleTvos.Configure,
    TargetAppleWatchos.Configure,
    TargetLinux.Configure,
    TargetMingw.Configure,
    TargetWasm.Configure,
    TargetAndroidNative.Configure,
    TargetWasmNative.Configure {

    public fun kotlin(action: KotlinProjectExtension.() -> Unit)

    public fun kotlinMultiplatform(action: KotlinMultiplatformExtension.() -> Unit)

    /**
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension.targetHierarchy
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetHierarchyDsl.default
     */
    public fun allDefaultTargets()
}
