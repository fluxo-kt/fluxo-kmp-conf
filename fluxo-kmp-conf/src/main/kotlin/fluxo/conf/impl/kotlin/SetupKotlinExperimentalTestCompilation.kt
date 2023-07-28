package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.d
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

internal val KotlinCompilation<*>.isExperimentalTestCompilation: Boolean
    get() = name == EXPERIMENTAL_TEST_COMPILATION_NAME

/**
 * Creates experimental test compilation for checking code with the latest
 * and experimental features enabled.
 */
internal fun NamedDomainObjectContainer<out KCompilation>.createExperimentalTestCompilation(
    mainCompilation: KCompilation,
    conf: FluxoConfigurationExtensionImpl,
) {
    // FIXME: check resulting compilations, add target name to the name

    create(EXPERIMENTAL_TEST_COMPILATION_NAME) {
        val project = conf.project
        project.logger.d("Creating $name compilation (experimental test)")

        defaultSourceSet.dependsOn(mainCompilation.defaultSourceSet)
        project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
            dependsOn(compileTaskProvider)
        }
    }
}

private const val EXPERIMENTAL_TEST_COMPILATION_NAME = "experimentalTest"
