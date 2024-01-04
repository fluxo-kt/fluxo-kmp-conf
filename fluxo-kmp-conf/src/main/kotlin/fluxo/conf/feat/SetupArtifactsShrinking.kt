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
internal fun setupArtifactsShrinking(
    conf: FluxoConfigurationExtensionImpl,
) {
    val isCalled = conf.ctx.startTaskNames.any {
        it.startsWith(SHRINKER_TASK_PREFIX) || it == SHRINKER_KEEP_GEN_TASK_NAME
    }
    val shrinkArtifacts = conf.shrinkArtifacts
    if (!shrinkArtifacts && !isCalled) {
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
    val runAfter = mutableListOf<Any>()

    // Auto-generate keep rules from API reports
    if (conf.shrinkingConfig.autoGenerateKeepRulesFromApis.get()) {
        val rulesGenRask = p.registerShrinkerKeepRulesGenTask()
        if (!shrinkArtifacts) {
            parents += rulesGenRask
        } else {
            runAfter += rulesGenRask
        }
    }

    val task = p.registerProguardTask(conf, parents, runAfter)

    if (shrinkArtifacts) {
        tasks.named(CHECK_TASK_NAME) {
            dependsOn(task)
        }
    }

    // FIXME: Support R8 minification
    // FIXME: Support replacing original artifacts with minified ones
    // FIXME: Run tests with minified artifacts
}
