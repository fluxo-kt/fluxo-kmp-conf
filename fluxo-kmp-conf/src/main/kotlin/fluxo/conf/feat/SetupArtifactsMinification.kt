package fluxo.conf.feat

import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.l
import fluxo.minification.registerProguardTask
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

@Suppress("ReturnCount")
internal fun setupArtifactsMinification(
    conf: FluxoConfigurationExtensionImpl,
) {
    val isCalled = conf.ctx.startTaskNames.any { it.startsWith("minify") }
    val minifyArtifacts = conf.minifyArtifacts
    if (!minifyArtifacts && !isCalled) {
        return
    }

    when (conf.mode) {
        // TODO: Support KMP JVM target minification with ProGuard
        ConfigurationType.KOTLIN_MULTIPLATFORM -> return

        // TODO: Support Android minification with ProGuard
        ConfigurationType.ANDROID_LIB,
        ConfigurationType.ANDROID_APP,
        -> return

        else -> {}
    }

    val p = conf.project
    p.logger.l("setup artifacts minification with ProGuard")

    val tasks = p.tasks
    val parentTask = tasks.named("jar")
    val task = p.registerProguardTask(conf, arrayOf(parentTask))

    if (minifyArtifacts) {
        tasks.named(CHECK_TASK_NAME) {
            dependsOn(task)
        }
    }
}

