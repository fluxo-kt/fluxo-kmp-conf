package fluxo.conf.dsl.container.impl

import fluxo.conf.dsl.container.KotlinTargetContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

internal interface KmpTargetContainer<T : KotlinTarget> :
    KotlinTargetContainer<T>,
    ContainerKotlinMultiplatformAware
