package fluxo.conf.dsl.container.target

import DEFAULT_JS_CONF
import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl

@FluxoKmpConfDsl
public class TargetJsContainer
private constructor(
    context: ContainerContext,
    targetName: String,
) : KmpTarget.NonJvm.CommonJs<KotlinJsTargetDsl>(context, targetName) {

    public var compilerType: KotlinJsCompilerType? = null

    public sealed interface Configure : ContainerHolderAware {

        public fun js(
            compiler: KotlinJsCompilerType? = null,
            targetName: String = "js",
            action: TargetJsContainer.() -> Unit = DEFAULT_JS_CONF,
        ) {
            holder.configure(targetName, ::TargetJsContainer) {
                compiler?.let { compilerType = it }
                action()
            }
        }
    }

    override fun setup(k: KotlinMultiplatformExtension) {
        val compilerType = compilerType ?: k.defaultJsCompilerType
        val target = k.js(name, compilerType, lazyTargetConf)

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

    override val sortOrder: Byte = 11
}
