@file:Suppress("DEPRECATION")

package fluxo.conf.dsl.container.target

import DEFAULT_COMMON_JS_CONFIGURATION
import fluxo.conf.dsl.container.KotlinTargetContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl

public interface JsTarget : KotlinTargetContainer<KotlinJsTargetDsl> {

    /**
     * Ignored: IR is the only Kotlin/JS compiler on every supported Kotlin version.
     *
     * Kept only so existing build scripts compile; KGP schedules [KotlinJsCompilerType]
     * for removal in Kotlin 2.6, and this property goes with it.
     */
    @Deprecated(JS_COMPILER_TYPE_DEPRECATION)
    public var compilerType: KotlinJsCompilerType?


    public interface Configure {

        public fun js(
            targetName: String = "js",
            configure: JsTarget.() -> Unit = DEFAULT_COMMON_JS_CONFIGURATION,
        )

        /**
         * [compiler] is ignored: IR is the only Kotlin/JS compiler on every supported
         * Kotlin version. `compiler` has no default, so `js()` and `js { }` resolve to the
         * overload without it.
         */
        @Deprecated(
            JS_COMPILER_TYPE_DEPRECATION,
            ReplaceWith("js(targetName, configure)"),
        )
        public fun js(
            compiler: KotlinJsCompilerType?,
            targetName: String = "js",
            configure: JsTarget.() -> Unit = DEFAULT_COMMON_JS_CONFIGURATION,
        ): Unit = js(targetName, configure)
    }
}

internal const val JS_COMPILER_TYPE_DEPRECATION =
    "Remove the compiler argument: IR is the only Kotlin/JS compiler, " +
        "and KGP removes KotlinJsCompilerType in Kotlin 2.6."
