package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetJs
import fluxo.conf.kmp.KmpTargetCode
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl

internal class TargetJsContainer(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<KotlinJsTargetDsl>(
    context, name, KmpTargetCode.JS, JS_SORT_ORDER,
), KmpTargetContainerImpl.NonJvm.CommonJs<KotlinJsTargetDsl>, TargetJs {

    override var compilerType: KotlinJsCompilerType? = null


    interface Configure : TargetJs.Configure, ContainerHolderAware {

        override fun js(
            compiler: KotlinJsCompilerType?,
            targetName: String,
            action: TargetJs.() -> Unit,
        ) {
            holder.configure(targetName, ::TargetJsContainer) {
                compiler?.let { compilerType = it }
                action()
            }
        }
    }

    override fun KotlinMultiplatformExtension.createTarget(): KotlinJsTargetDsl {
        val compilerType = compilerType ?: defaultJsCompilerType
        return js(name, compilerType, lazyTargetConf)
    }
}
