package fluxo.conf.dsl.container.impl

import fluxo.conf.dsl.container.Container
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

internal interface ContainerKotlinAware<E : KotlinProjectExtension> : Container {

    fun setup(k: E)
}
