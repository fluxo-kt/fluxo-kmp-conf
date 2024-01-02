package fluxo.conf.feat

import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.l
import fluxo.minification.SHRINKER_KEEP_GEN_TASK_NAME
import fluxo.minification.SHRINKER_TASK_PREFIX
import fluxo.minification.registerProguardTask
import fluxo.minification.registerShrinkerKeepRulesGenTask
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

@Suppress("ReturnCount")
internal fun setupArtifactsMinification(
    conf: FluxoConfigurationExtensionImpl,
) {
    val isCalled = conf.ctx.startTaskNames.any {
        it.startsWith(SHRINKER_TASK_PREFIX) || it == SHRINKER_KEEP_GEN_TASK_NAME
    }
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
    val parents = mutableListOf<Any>("jar")

    // Auto-generate keep rules from API reports
    if (conf.minificationConfig.autoGenerateKeepRulesFromApis.get()) {
        // FIXME: Finalize API reports generation with this task
        parents += p.registerShrinkerKeepRulesGenTask()
    }

    val task = p.registerProguardTask(conf, parents)

    if (minifyArtifacts) {
        tasks.named(CHECK_TASK_NAME) {
            dependsOn(task)
        }
    }
}

