package fluxo.conf.dsl.container.impl

import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

internal class KotlinProjectActionContainer(context: ContainerContext, name: String = NAME) :
    CustomTypeContainer<KotlinProjectExtension>(context, name),
    ContainerKotlinAware<KotlinProjectExtension> {

    override fun setup(k: KotlinProjectExtension) = setupCustom(k)

    companion object {
        const val NAME = "kotlinExtension"
    }
}
