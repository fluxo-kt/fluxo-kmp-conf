package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.impl.configureExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

@FluxoKmpConfDsl
public class TargetJvmContainer
private constructor(
    context: ContainerContext,
    targetName: String,
) : KmpTarget.CommonJvm<KotlinJvmTarget>(context, targetName) {

    public sealed interface Configure : ContainerHolderAware {

        public fun jvm(
            targetName: String = "jvm",
            action: TargetJvmContainer.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::TargetJvmContainer, action)
        }
    }

    override fun setup(k: KotlinMultiplatformExtension) {
        val target = k.jvm(name) {
            kotlinJvmTarget?.toString()?.let { version ->
                compilations.all {
                    kotlinOptions.jvmTarget = version
                }
            }

            lazyTargetConf()

            if (!withJavaEnabled) {
                return@jvm
            }

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

        applyPlugins(target.project)

        with(k.sourceSets) {
            getByName("${name}Main") {
                dependsOn(getByName("${COMMON_JVM}Main"))
                lazySourceSetMainConf()
            }
            getByName("${name}Test") {
                dependsOn(getByName("${COMMON_JVM}Test"))
                lazySourceSetTestConf()
            }
        }
    }

    override val sortOrder: Byte = 2
}
