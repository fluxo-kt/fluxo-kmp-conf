package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KotlinTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetWasmNative
import fluxo.conf.target.KmpTargetCode
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

internal class TargetWasmNativeContainer(
    context: ContainerContext, name: String,
) : KotlinTargetContainerImpl<KotlinNativeTarget>(
    context, name, KmpTargetCode.WASM_32, WASM_NATIVE_SORT_ORDER,
), KotlinTargetContainerImpl.NonJvm.Native.WasmNative, TargetWasmNative {

    interface Configure : TargetWasmNative.Configure, ContainerHolderAware {

        @Suppress("OVERRIDE_DEPRECATION")
        override fun wasm32(targetName: String, action: TargetWasmNative.() -> Unit) {
            holder.configure(targetName, ::TargetWasmNativeContainer, action)
        }
    }


    @Suppress("DEPRECATION")
    override fun KotlinMultiplatformExtension.createTarget() = createTarget(::wasm32)


    internal companion object {
        internal const val WASM_NATIVE = "wasmNative"
    }
}
