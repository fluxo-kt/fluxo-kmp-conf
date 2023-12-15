package fluxo.conf.dsl.container.impl

import common
import dependsOn
import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.kmp.SourceSetBundle
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

internal interface KmpTargetContainer<T : KotlinTarget> :
    KotlinTargetContainer<T>,
    ContainerKotlinMultiplatformAware {

    val allowManualHierarchy: Boolean

    /**
     *
     * @see KotlinMultiplatformExtension.applyDefaultHierarchyTemplate
     * @see KotlinMultiplatformExtension.applyHierarchyTemplate
     * @see org.jetbrains.kotlin.gradle.plugin.defaultKotlinHierarchyTemplate
     * @see org.jetbrains.kotlin.gradle.plugin.mpp.targetHierarchy.applyKotlinTargetHierarchy
     * @see org.jetbrains.kotlin.gradle.plugin.mpp.targetHierarchy.buildKotlinTargetHierarchy
     * @see org.jetbrains.kotlin.gradle.plugin.mpp.targetHierarchy.KotlinTargetHierarchyBuilderImpl
     */
    fun setupParentSourceSet(k: KotlinMultiplatformExtension, child: SourceSetBundle) {
        // TODO: Create common bundle once and reuse?
        if (allowManualHierarchy) {
            @Suppress("DEPRECATION")
            child dependsOn k.sourceSets.common
        }
    }
}
