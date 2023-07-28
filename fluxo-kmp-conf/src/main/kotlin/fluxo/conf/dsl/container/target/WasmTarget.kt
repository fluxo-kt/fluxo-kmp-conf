package fluxo.conf.dsl.container.target

import DEFAULT_COMMON_JS_CONFIGURATION
import fluxo.conf.dsl.container.KotlinTargetContainer
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl

public interface WasmTarget : KotlinTargetContainer<KotlinWasmTargetDsl> {

    public interface Configure {

        @ExperimentalWasmDsl
        @SinceKotlin("1.8.20")
        @Suppress("NEWER_VERSION_IN_SINCE_KOTLIN")
        public fun wasm(
            targetName: String = "wasm",
            action: WasmTarget.() -> Unit = DEFAULT_COMMON_JS_CONFIGURATION,
        )
    }
}
