package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.WasmTarget
import fluxo.conf.impl.kotlin.KOTLIN_1_8_20
import fluxo.conf.impl.kotlin.KOTLIN_1_9_20
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmWasiTargetDsl

internal abstract class TargetWasmContainer<T : KotlinWasmTargetDsl>(
    context: ContainerContext,
    name: String,
) : KmpTargetContainerImpl<T>(context, name, WASM_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.CommonJs.CommonWasm<T>,
    WasmTarget<T> {

    interface Configure : WasmTarget.Configure, ContainerHolderAware {

        @ExperimentalWasmDsl
        override fun wasmJs(
            targetName: String,
            configure: WasmTarget<KotlinWasmJsTargetDsl>.() -> Unit,
        ) {
            if (holder.kotlinPluginVersion < KOTLIN_1_8_20) {
                throw GradleException("wasmJs requires Kotlin 1.8.20 or greater")
            }
            holder.configure(targetName, ::WasmJs, KmpTargetCode.WASM_JS, configure)
        }

        @ExperimentalWasmDsl
        override fun wasmWasi(
            targetName: String,
            configure: WasmTarget<KotlinWasmWasiTargetDsl>.() -> Unit,
        ) {
            if (holder.kotlinPluginVersion < KOTLIN_1_9_20) {
                throw GradleException("wasmWasi requires Kotlin 1.9.20 or greater")
            }
            holder.configure(targetName, ::WasmWasi, KmpTargetCode.WASM_WASI, configure)
        }
    }


    class WasmJs(context: ContainerContext, targetName: String) :
        TargetWasmContainer<KotlinWasmJsTargetDsl>(context, targetName) {

        @ExperimentalWasmDsl
        override fun KotlinMultiplatformExtension.createTarget(): KotlinWasmJsTargetDsl {
            // wasm target was renamed to wasm-js in Kotlin 1.9.20; the legacy `wasm`
            // factory was removed for Kotlin 2.3+. Consumer floor is now Kotlin 2.1+.
            return wasmJs(name, lazyTargetConf)
        }
    }

    class WasmWasi(context: ContainerContext, targetName: String) :
        TargetWasmContainer<KotlinWasmWasiTargetDsl>(context, targetName) {

        @ExperimentalWasmDsl
        override fun KotlinMultiplatformExtension.createTarget(): KotlinWasmWasiTargetDsl =
            createTarget(::wasmWasi)
    }
}
