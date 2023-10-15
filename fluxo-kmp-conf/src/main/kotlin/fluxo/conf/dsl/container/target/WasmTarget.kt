package fluxo.conf.dsl.container.target

import DEFAULT_COMMON_JS_CONFIGURATION
import fluxo.conf.dsl.container.KotlinTargetContainer
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl

// FIXME: wasmWasi and common wasm parent

public interface WasmTarget : KotlinTargetContainer<KotlinWasmJsTargetDsl> {

    public interface Configure {

        /**
         *
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithWasmPresetFunctions.wasm
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithWasmPresetFunctions.wasmJs
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithWasmPresetFunctions.wasmWasi
         */
        @ExperimentalWasmDsl
        @SinceKotlin("1.8.20")
        @Suppress("NEWER_VERSION_IN_SINCE_KOTLIN")
        public fun wasmJs(
            targetName: String = "wasmJs",
            action: WasmTarget.() -> Unit = DEFAULT_COMMON_JS_CONFIGURATION,
        )
    }
}
