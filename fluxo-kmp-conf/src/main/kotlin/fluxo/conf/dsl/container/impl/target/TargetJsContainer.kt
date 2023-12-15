package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.JsTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl

internal class TargetJsContainer(
    context: ContainerContext,
    name: String,
) : KmpTargetContainerImpl<KotlinJsTargetDsl>(context, name, JS_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.CommonJs<KotlinJsTargetDsl>,
    JsTarget {

    override var compilerType: KotlinJsCompilerType? = null


    interface Configure : JsTarget.Configure, ContainerHolderAware {

        override fun js(
            compiler: KotlinJsCompilerType?,
            targetName: String,
            configure: JsTarget.() -> Unit,
        ) {
            val f = compiler?.let {
                {
                    compilerType = it
                    configure()
                }
            } ?: configure

            holder.configure(targetName, ::TargetJsContainer, KmpTargetCode.JS, f)
        }
    }

    override fun KotlinMultiplatformExtension.createTarget(): KotlinJsTargetDsl {
        val compilerType = compilerType ?: defaultJsCompilerType
        return js(name, compilerType, lazyTargetConf)
    }
}
