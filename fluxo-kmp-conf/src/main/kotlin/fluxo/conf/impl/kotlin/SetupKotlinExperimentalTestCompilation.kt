package fluxo.conf.impl.kotlin

import fluxo.conf.impl.capitalizeAsciiOnly
import fluxo.conf.impl.d
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

internal val KotlinCompilation<*>.isExperimentalTestCompilation: Boolean
    get() = name.let { it.startsWith(PREFIX) && it.endsWith(POSTFIX) }

/**
 * Creates experimental test compilation for checking code with the latest
 * and experimental features enabled.
 *
 * @receiver Main compilation to create experimental test compilation from.
 */
internal fun KCompilation.createExperimentalTestCompilation(
    compilations: NamedDomainObjectContainer<out KCompilation>,
    isMultiplatform: Boolean,
) {
    val platform = if (isMultiplatform) platformType.name.capitalizeAsciiOnly() else ""
    val compilationName = "$PREFIX$platform$POSTFIX"
    val mainCompilation = this
    compilations.create(compilationName) {
        val project = mainCompilation.project
        project.logger.d("Creating $name compilation (experimental test)")
        defaultSourceSet.dependsOn(mainCompilation.defaultSourceSet)
        project.tasks.named(CHECK_TASK_NAME) {
            dependsOn(compileTaskProvider)
        }
    }
}

private const val PREFIX = "experimental"
private const val POSTFIX = "Latest"
