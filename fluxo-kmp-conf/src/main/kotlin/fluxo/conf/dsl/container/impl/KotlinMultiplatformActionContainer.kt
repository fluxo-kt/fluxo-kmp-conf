package fluxo.conf.dsl.container.impl

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal class KotlinMultiplatformActionContainer(context: ContainerContext, name: String = NAME) :
    CustomTypeContainer<KotlinMultiplatformExtension>(context, name),
    ContainerKotlinMultiplatformAware {

    override fun setup(k: KotlinMultiplatformExtension) = setupCustom(k)

    companion object {
        const val NAME = "kmpExtension"
    }
}
