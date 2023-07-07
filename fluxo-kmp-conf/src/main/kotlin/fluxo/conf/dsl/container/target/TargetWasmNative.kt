package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.kmp.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

public interface TargetWasmNative : KotlinTargetContainer<KotlinNativeTarget> {

    public interface Configure {
        @Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun wasmNative(action: TargetWasmNative.() -> Unit = EMPTY_FUN) {
            wasm32(action = action)
        }

        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun wasm32(
            targetName: String = "wasm32",
            action: TargetWasmNative.() -> Unit = EMPTY_FUN,
        )
    }
}
