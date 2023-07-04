package fluxo.conf.dsl.container.impl

import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

internal interface ContainerKotlinSingleTargetAware<E : KotlinSingleTargetExtension<KotlinTarget>> :
    ContainerKotlinAware<E>
