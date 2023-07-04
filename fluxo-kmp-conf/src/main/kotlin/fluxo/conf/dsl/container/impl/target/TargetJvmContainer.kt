package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KotlinTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetJvm
import fluxo.conf.impl.configureExtension
import fluxo.conf.target.KmpTargetCode
import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal class TargetJvmContainer(
    context: ContainerContext, name: String,
) : KotlinTargetContainerImpl<KotlinJvmTarget>(
    context, name, KmpTargetCode.JVM, JVM_SORT_ORDER,
), KotlinTargetContainerImpl.CommonJvm<KotlinJvmTarget>, TargetJvm {

    interface Configure : TargetJvm.Configure, ContainerHolderAware {

        override fun jvm(
            targetName: String,
            action: TargetJvm.() -> Unit,
        ) {
            holder.configure(targetName, ::TargetJvmContainer, action)
        }
    }

    override fun KotlinMultiplatformExtension.createTarget() = jvm(name) {
        // FIXME: Replace with full-fledged context-based target configuration
        kotlinJvmTarget?.toString()?.let { version ->
            compilations.all {
                kotlinOptions.jvmTarget = version
            }
        }

        lazyTargetConf()

        if (!withJavaEnabled) {
            return@jvm
        }

        // FIXME: Replace with full-fledged context-based target configuration
        val sCompatibility = compileSourceCompatibility
        val tCompatibility = compileTargetCompatibility
        if (sCompatibility == null && tCompatibility == null) {
            return@jvm
        }

        project.configureExtension<JavaPluginExtension> {
            if (sCompatibility != null) {
                sourceCompatibility = sCompatibility
            }
            if (tCompatibility != null) {
                targetCompatibility = tCompatibility
            }
        }
    }
}
