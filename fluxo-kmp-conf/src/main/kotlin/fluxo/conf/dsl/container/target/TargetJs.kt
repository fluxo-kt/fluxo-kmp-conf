package fluxo.conf.dsl.container.target

import DEFAULT_COMMON_JS_CONFIGURATION
import fluxo.conf.dsl.container.KotlinTargetContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl

public interface TargetJs : KotlinTargetContainer<KotlinJsTargetDsl> {

    /**
     *
     * @see org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerTypeHolder.defaultJsCompilerType
     */
    public var compilerType: KotlinJsCompilerType?


    public interface Configure {

        public fun js(
            compiler: KotlinJsCompilerType? = null,
            targetName: String = "js",
            action: TargetJs.() -> Unit = DEFAULT_COMMON_JS_CONFIGURATION,
        )
    }
}
