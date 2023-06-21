@file:Suppress("ClassName", "DeprecatedCallableAddReplaceWith", "DEPRECATION")

package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.target.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

@FluxoKmpConfDsl
public sealed class TargetWasmNativeContainer
private constructor(
    context: ContainerContext,
    targetName: String,
) : KmpTarget.NonJvm.Native.Wasm<KotlinNativeTarget>(context, targetName) {

    public sealed interface Configure : ContainerHolderAware {

        @Deprecated(message = DEPRECATED_TARGET_MSG)
        public fun wasmNativeAll() {
            wasm32()
        }

        @Deprecated(message = DEPRECATED_TARGET_MSG)
        public fun wasm32(
            targetName: String = "wasm32",
            action: TargetWasmNativeContainer.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::TargetWasmNativeContainer, action)
        }
    }

    final override fun KotlinMultiplatformExtension.setup() {
        val target = wasm32(name, lazyTargetConf)

        applyPlugins(target.project)

        with(sourceSets) {
            getByName("${name}Main") {
                dependsOn(getByName("${WASM_NATIVE}Main"))
                lazySourceSetMainConf()
            }
            getByName("${name}Test") {
                dependsOn(getByName("${WASM_NATIVE}Test"))
                lazySourceSetTestConf()
            }
        }
    }

    final override val sortOrder: Byte = 61

    private companion object {
        private const val WASM_NATIVE = "wasmNative"
    }
}
