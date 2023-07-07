package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetWasm
import fluxo.conf.kmp.KmpTargetCode
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl

internal class TargetWasmContainer(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<KotlinWasmTargetDsl>(
    context, name, KmpTargetCode.WASM, WASM_SORT_ORDER,
), KmpTargetContainerImpl.NonJvm.CommonJs<KotlinWasmTargetDsl>, TargetWasm {

    interface Configure : TargetWasm.Configure, ContainerHolderAware {

        @ExperimentalWasmDsl
        override fun wasm(
            targetName: String,
            action: TargetWasm.() -> Unit,
        ) {
            @Suppress("MagicNumber")
            if (!holder.kotlinPluginVersion.isAtLeast(1, 7, 20)) {
                throw GradleException("wasm requires Kotlin 1.7.20 or greater")
            }
            holder.configure(targetName, ::TargetWasmContainer, action)
        }
    }

    @ExperimentalWasmDsl
    override fun KotlinMultiplatformExtension.createTarget() =
        wasm(name, lazyTargetConf) as KotlinWasmTargetDsl
}
