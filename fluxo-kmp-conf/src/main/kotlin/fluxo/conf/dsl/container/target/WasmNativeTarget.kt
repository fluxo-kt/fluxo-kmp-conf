package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

public interface WasmNativeTarget : KotlinTargetContainer<KotlinNativeTarget> {

    public interface Configure {
        @Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun wasmNative(action: WasmNativeTarget.() -> Unit = EMPTY_FUN) {
            wasm32(action = action)
        }

        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun wasm32(
            targetName: String = "wasm32",
            action: WasmNativeTarget.() -> Unit = EMPTY_FUN,
        )
    }
}
