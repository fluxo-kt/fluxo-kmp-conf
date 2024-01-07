package fluxo.conf.feat

import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.l
import fluxo.minification.SHRINKER_KEEP_GEN_TASK_NAME
import fluxo.minification.SHRINKER_TASK_PREFIX
import fluxo.minification.Shrinker
import fluxo.minification.registerShrinkerKeepRulesGenTask
import fluxo.minification.registerShrinkerTask
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

// region Notes and references:
// https://r8.googlesource.com/r8/
// https://r8-docs.preemptive.com/
// https://www.guardsquare.com/manual/configuration/usage
// https://github.com/GradleUp/gr8/blob/cb007cd/plugin-common/src/main/kotlin/com/gradleup/gr8/Gr8Configurator.kt#L183
// https://android.googlesource.com/platform/tools/base/+/0d60339/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/tasks/R8Task.kt
// https://github.com/avito-tech/avito-android/blob/a1949b4/subprojects/assemble/proguard-guard/src/main/kotlin/com/avito/android/proguard_guard/shadowr8/ShadowR8TaskCreator.kt
// https://github.com/lowasser/kotlinx.coroutines/blob/fcaa6df/buildSrc/src/main/kotlin/RunR8.kt
//
// ProGuard/R8 configuration improvements
// https://github.com/Guardsquare/proguard/tree/8afa59e/gradle-plugin/src/main
// https://github.com/JetBrains/kotlin/blob/0926eba/libraries/tools/kotlin-main-kts/build.gradle.kts#L84
// https://github.com/JetBrains/compose-multiplatform/blob/50d45f3/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/internal/configureJvmApplication.kt#L241
// https://github.com/JetBrains/compose-multiplatform/blob/b67dde7/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/tasks/AbstractProguardTask.kt#L22
// https://github.com/TWiStErRob/net.twisterrob.inventory/blob/cc4eb02/gradle/plugins-inventory/src/main/kotlin/net/twisterrob/inventory/build/unfuscation/UnfuscateTask.kt#L33
// https://github.com/SgtSilvio/gradle-proguard/blob/36e9437/src/main/kotlin/io/github/sgtsilvio/gradle/proguard/ProguardTask.kt#L39
// https://github.com/ArcticLampyrid/gradle-git-version/blob/23ccfc8/build.gradle.kts#L72
// endregion

/**
 *
 * @see com.android.tools.r8.R8
 * @see com.android.tools.r8.Version
 * @see proguard.ProGuard
 * @see proguard.ProGuard.getVersion
 */
internal fun setupArtifactsShrinking(
    conf: FluxoConfigurationExtensionImpl,
) {
    // TODO: Allow to call any shrinker by task name
    val isCalled = conf.ctx.startTaskNames.any {
        it.startsWith(SHRINKER_TASK_PREFIX) || it == SHRINKER_KEEP_GEN_TASK_NAME
    }
    val shrinkArtifacts = conf.shrinkArtifacts
    if (!shrinkArtifacts && !isCalled || modeIsNotSupported(conf)) {
        return
    }

    val p = conf.project
    val logger = p.logger
    logger.l("Setup artifacts shrinking")

    val tasks = p.tasks
    val parents = mutableListOf<Any>("jar")
    val runAfter = mutableListOf<Any>()

    // Auto-generate keep rules from API reports
    val settings = conf.shrinkingConfig
    if (settings.autoGenerateKeepRulesFromApis.get()) {
        val rulesGenRask = p.registerShrinkerKeepRulesGenTask()
        if (!shrinkArtifacts) {
            parents += rulesGenRask
        } else {
            runAfter += rulesGenRask
        }
    }

    val shrinkTask = p.registerShrinkerTask(conf, parents, runAfter)
    val shrinkTasks = mutableListOf(shrinkTask)

    if (USE_BOTH || settings.useBothShrinkers.get()) {
        logger.l("Both shrinker tasks added!")
        val shrinker = if (settings.useR8.get()) Shrinker.ProGuard else Shrinker.R8
        shrinkTasks += p.registerShrinkerTask(conf, parents, runAfter, forceShrinker = shrinker)
    }

    if (shrinkArtifacts) {
        tasks.named(CHECK_TASK_NAME) {
            dependsOn(shrinkTasks)
        }
    }

    // Replace the original artifact with shrinked one.
    // Replaces the default jar in outgoingVariants.
    if (settings.replaceOutgoingJar.get()) {
        logger.l("Replace outgoing jar with shrinked one")
        p.configurations.configureEach {
            outgoing {
                val removed = artifacts.removeIf { it.classifier.isNullOrEmpty() }
                if (removed) {
                    artifact(shrinkTask.flatMap { it.destinationFile }) {
                        // Pom and maven consumers do not like the
                        // `-all` or `-shadowed` classifiers.
                        classifier = ""
                    }
                }
            }
        }
    }

    // FIXME: Run tests with minified artifacts
    //  https://github.com/ArcticLampyrid/gradle-git-version/blob/23ccfc8/build.gradle.kts#L72
}

private fun modeIsNotSupported(conf: FluxoConfigurationExtensionImpl) = when (conf.mode) {
    // TODO: Support KMP JVM target minification with ProGuard
    ConfigurationType.KOTLIN_MULTIPLATFORM -> true

    // TODO: Support Android minification with ProGuard
    ConfigurationType.ANDROID_LIB,
    ConfigurationType.ANDROID_APP,
    -> true

    else -> false
}

private const val USE_BOTH = false
