package fluxo.conf.dsl.container.target

import DEFAULT_JS_CONF
import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl

@FluxoKmpConfDsl
public class TargetWasmContainer
private constructor(
    context: ContainerContext,
    targetName: String,
) : KmpTarget.NonJvm.CommonJs<KotlinWasmTargetDsl>(context, targetName) {

    public sealed interface Configure : ContainerHolderAware {

        @ExperimentalWasmDsl
        @SinceKotlin("1.7.20")
        @Suppress("NEWER_VERSION_IN_SINCE_KOTLIN")
        public fun wasm(
            targetName: String = "wasm",
            action: TargetWasmContainer.() -> Unit = DEFAULT_JS_CONF,
        ) {
            @Suppress("MagicNumber")
            if (!holder.kotlinPluginVersion.isAtLeast(1, 7, 20)) {
                throw GradleException("wasm requires Kotlin 1.7.20 or greater")
            }
            holder.configure(targetName, ::TargetWasmContainer, action)
        }
    }

    @ExperimentalWasmDsl
    override fun setup(k: KotlinMultiplatformExtension) {
        val target = k.wasm(name, lazyTargetConf)

        applyPlugins(target.project)

        with(k.sourceSets) {
            getByName("${name}Main") {
                dependsOn(getByName("${COMMON_JS}Main"))
                lazySourceSetMainConf()
            }
            getByName("${name}Test") {
                dependsOn(getByName("${COMMON_JS}Test"))
                lazySourceSetTestConf()
            }
        }
    }

    override val sortOrder: Byte = 12
}
