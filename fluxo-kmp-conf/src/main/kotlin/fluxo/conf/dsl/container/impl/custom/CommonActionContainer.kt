package fluxo.conf.dsl.container.impl.custom

import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerKotlinAware
import fluxo.conf.dsl.container.impl.CustomTypeContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

internal class CommonActionContainer(context: ContainerContext, name: String = NAME) :
    CustomTypeContainer<Container>(context, name),
    ContainerKotlinAware<KotlinProjectExtension> {

    override fun setup(k: KotlinProjectExtension) = setupCustom(this)

    companion object {
        const val NAME = "commonExtension"
    }
}
