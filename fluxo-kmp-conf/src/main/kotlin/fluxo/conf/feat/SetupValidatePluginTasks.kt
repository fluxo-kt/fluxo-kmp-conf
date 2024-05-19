package fluxo.conf.feat

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.withType
import fluxo.gradle.addToCheckAndTestDependencies
import org.gradle.api.Project
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import org.gradle.plugin.devel.tasks.ValidatePlugins

/**
 *
 * @see JavaGradlePluginPlugin.configurePluginValidations
 */
internal fun Project.setupValidatePluginTasks(conf: FluxoConfigurationExtensionImpl) {
    val disable = conf.ctx.testsDisabled || !conf.setupVerification

    // Task added by `java-gradle-plugin`.
    val tasks = tasks.withType<ValidatePlugins> c@{
        if (disable) {
            enabled = false
            return@c
        }

        logger.info("Configuring ValidatePlugins task: $name")

        failOnWarning.set(true)
        enableStricterValidation.set(true)
    }
    if (!disable) {
        addToCheckAndTestDependencies(tasks)
    }

    // TODO: Multiset configuration
    //  https://github.com/JetBrains/kotlin/blob/f6d2151/repo/gradle-build-conventions/buildsrc-compat/src/main/kotlin/GradleCommon.kt#L789
}
