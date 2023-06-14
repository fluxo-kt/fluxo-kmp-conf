package fluxo.conf.dsl.container.target

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

    public sealed interface Configure : CommonJs.Configure {

        public fun js(
            targetName: String = "js",
            compiler: KotlinJsCompilerType? = null,
            action: TargetJsContainer.() -> Unit = {
                target {
                    testTimeout()
                }
            },
        ) {
            holder.configure(targetName, ::TargetJsContainer) {
                compiler?.let { compilerType = it }
                action()
            }
        }
    }

    override fun KotlinMultiplatformExtension.setup() {
        val compilerType = compilerType ?: defaultJsCompilerType
        val target = js(name, compilerType, lazyTargetConf)

        applyPlugins(target.project)

        with(sourceSets) {
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
