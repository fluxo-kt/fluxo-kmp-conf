package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.WasmTarget
import fluxo.conf.impl.kotlin.KOTLIN_1_8_20
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl

internal class TargetWasmContainer(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<KotlinWasmTargetDsl>(context, name, WASM_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.CommonJs<KotlinWasmTargetDsl>, WasmTarget {

    interface Configure : WasmTarget.Configure, ContainerHolderAware {

        @ExperimentalWasmDsl
        override fun wasm(
            targetName: String,
            action: WasmTarget.() -> Unit,
        ) {
            if (holder.kotlinPluginVersion < KOTLIN_1_8_20) {
                throw GradleException("wasm requires Kotlin 1.8.20 or greater")
            }
            holder.configure(targetName, ::TargetWasmContainer, KmpTargetCode.WASM, action)
        }
    }

    @ExperimentalWasmDsl
    override fun KotlinMultiplatformExtension.createTarget() =
        wasm(name, lazyTargetConf) as KotlinWasmTargetDsl
}
