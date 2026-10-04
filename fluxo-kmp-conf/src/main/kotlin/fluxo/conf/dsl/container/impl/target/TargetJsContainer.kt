@file:Suppress("DEPRECATION")

package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.JS_COMPILER_TYPE_DEPRECATION
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

    @Deprecated(JS_COMPILER_TYPE_DEPRECATION)
    override var compilerType: KotlinJsCompilerType? = null


    interface Configure : JsTarget.Configure, ContainerHolderAware {

        override fun js(targetName: String, configure: JsTarget.() -> Unit) {
            holder.configure(targetName, ::TargetJsContainer, KmpTargetCode.JS, configure)
        }
    }

    // The name-only overload: the compiler-type one is deprecated by KGP and removed in 2.6.
    override fun KotlinMultiplatformExtension.createTarget(): KotlinJsTargetDsl =
        js(name, lazyTargetConf)
}
