package fluxo.conf.dsl.container.target

import DEFAULT_COMMON_JS_CONFIGURATION as DEFAULT_CONF
import fluxo.conf.dsl.container.KotlinTargetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithWasmPresetFunctions
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmWasiTargetDsl

public interface WasmTarget<out T : KotlinWasmTargetDsl> : KotlinTargetContainer<T> {

    public interface Configure {

        /**
         *
         * @see KotlinTargetContainerWithWasmPresetFunctions.wasm
         * @see KotlinTargetContainerWithWasmPresetFunctions.wasmJs
         */
        @ExperimentalWasmDsl
        @SinceKotlin("1.8.20")
        @Suppress("NEWER_VERSION_IN_SINCE_KOTLIN")
        public fun wasmJs(
            targetName: String = "wasmJs",
            action: WasmTarget<KotlinWasmJsTargetDsl>.() -> Unit = DEFAULT_CONF,
        )

        /**
         *
         * @see KotlinTargetContainerWithWasmPresetFunctions.wasmWasi
         */
        @ExperimentalWasmDsl
        @SinceKotlin("1.9.20")
        @Suppress("NEWER_VERSION_IN_SINCE_KOTLIN")
        public fun wasmWasi(
            targetName: String = "wasmWasi",
            action: WasmTarget<KotlinWasmWasiTargetDsl>.() -> Unit = DEFAULT_CONF,
        )
    }
}
