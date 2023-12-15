package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal class TargetJvmContainer(
    context: ContainerContext,
    name: String,
) : KmpTargetContainerImpl<KotlinJvmTarget>(context, name, JVM_SORT_ORDER),
    KmpTargetContainerImpl.CommonJvm<KotlinJvmTarget>,
    JvmTarget {

    interface Configure : JvmTarget.Configure, ContainerHolderAware {

        override fun jvm(
            targetName: String,
            configure: JvmTarget.() -> Unit,
        ) {
            holder.configure(targetName, ::TargetJvmContainer, KmpTargetCode.JVM, configure)
        }
    }

    override fun KotlinMultiplatformExtension.createTarget() = createTarget(::jvm)
}
