package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.WasmNativeTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

internal class TargetWasmNativeContainer(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<KotlinNativeTarget>(context, name, WASM_NATIVE_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.Native.WasmNative, WasmNativeTarget {

    interface Configure : WasmNativeTarget.Configure, ContainerHolderAware {

        @Suppress("OVERRIDE_DEPRECATION")
        override fun wasm32(targetName: String, action: WasmNativeTarget.() -> Unit) {
            holder.configure(targetName, ::TargetWasmNativeContainer, KmpTargetCode.WASM32, action)
        }
    }


    @Suppress("DEPRECATION")
    override fun KotlinMultiplatformExtension.createTarget() = createTarget(::wasm32)
}
