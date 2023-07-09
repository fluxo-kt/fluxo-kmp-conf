package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetJvm
import fluxo.conf.kmp.KmpTargetCode
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal class TargetJvmContainer(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<KotlinJvmTarget>(
    context, name, KmpTargetCode.JVM, JVM_SORT_ORDER,
), KmpTargetContainerImpl.CommonJvm<KotlinJvmTarget>, TargetJvm {

    interface Configure : TargetJvm.Configure, ContainerHolderAware {

        override fun jvm(
            targetName: String,
            action: TargetJvm.() -> Unit,
        ) {
            holder.configure(targetName, ::TargetJvmContainer, action)
        }
    }

    override fun KotlinMultiplatformExtension.createTarget() = createTarget(::jvm)
}
