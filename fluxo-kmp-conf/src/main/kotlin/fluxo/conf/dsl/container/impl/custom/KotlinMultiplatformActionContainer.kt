package fluxo.conf.dsl.container.impl.custom

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerKotlinMultiplatformAware
import fluxo.conf.dsl.container.impl.CustomTypeContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal class KotlinMultiplatformActionContainer(context: ContainerContext, name: String = NAME) :
    CustomTypeContainer<KotlinMultiplatformExtension>(context, name),
    ContainerKotlinMultiplatformAware {

    override fun setup(k: KotlinMultiplatformExtension) = setupCustom(k)

    companion object {
        const val NAME = "kmpExtension"
    }
}
