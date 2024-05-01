package fluxo.artifact.proc

import fluxo.artifact.dsl.ArtifactProcessorConfigImpl
import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.feat.API_DIR
import fluxo.conf.feat.bindToApiDumpTasks
import fluxo.conf.impl.register
import fluxo.log.l
import fluxo.log.w
import fluxo.shrink.SHRINKER_KEEP_GEN_TASK_NAME
import fluxo.shrink.ShrinkerVerificationTestTask
import fluxo.shrink.getShrinkerVerifyTaskName
import fluxo.shrink.registerShrinkerKeepRulesGenTask
import fluxo.shrink.registerShrinkerTask
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

// FIXME: Create a marker task for the main processing chain with a stable name.

// FIXME: Run tests with minified artifacts.
//  https://github.com/ArcticLampyrid/gradle-git-version/blob/23ccfc8/build.gradle.kts#L72

// FIXME: Ensure the obfuscation mapping incrementality and compatibility when double-shrinking.

// FIXME: Support auto-loading of the shrinking rules from the classpath for the ProGuard.
//  Should be reused from the Android ProGuard Gradle plugin.
//  https://github.com/Kotlin/kotlinx.coroutines/tree/2ddcbe8/kotlinx-coroutines-core/jvm/resources/META-INF
//  https://github.com/JetBrains/kotlin/tree/14b13a2/core/reflection.jvm/resources/META-INF/com.android.tools
//  https://github.com/search?type=code&q=path%3AMETA-INF%2Fcom.android.tools%2Fr8**.pro
//  https://github.com/search?type=code&q=path%3AMETA-INF%2F**.pro

// FIXME: Support KMP JVM target minification with ProGuard.
// FIXME: Verify proper replacement of the original artifact with the processed one (map by file?).

// FIXME: Check 'reproducibleArtifacts' with shrinker enabled.

// TODO: Support auto-disabling kotlin null checks generation for the shrunken release builds.
//  Auto-add assumenosideeffects to remove left intrinsics in that case.

// FIXME: Publish the debug non-shrunken artifacts alongside the release shrunken ones.
//  Should be easy to switch between them in the consuming projects.
//  Use variant attributes?

// TODO: Allow to call shrinker by task name even if it's disabled.
//  Or, at least, show an informative warning when it's disabled

// FIXME: Support shadow jar generation before shrinking artifacts.
//  https://github.com/GradleUp/gr8
//  https://github.com/johnrengelman/shadow

// TODO: Support Android minification with Fluxo processing chain.

// FIXME: Support final artifact filtration step similar to the Android packaging options.
// e.g. android.packaging.resources.excludes

// region Notes and references:
// https://r8.googlesource.com/r8/
// https://r8-docs.preemptive.com/
// https://www.guardsquare.com/manual/configuration/usage
// https://github.com/GradleUp/gr8/blob/cb007cd/plugin-common/src/main/kotlin/com/gradleup/gr8/Gr8Configurator.kt#L183
// https://android.googlesource.com/platform/tools/base/+/0d60339/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/tasks/R8Task.kt
// https://github.com/avito-tech/avito-android/blob/a1949b4/subprojects/assemble/proguard-guard/src/main/kotlin/com/avito/android/proguard_guard/shadowr8/ShadowR8TaskCreator.kt
// https://github.com/lowasser/kotlinx.coroutines/blob/fcaa6df/buildSrc/src/main/kotlin/RunR8.kt
// https://github.com/tuuzed/LightTunnel/blob/680d3bc/buildSrc/src/main/kotlin/Compiler.kt
//
// https://slackhq.github.io/keeper/
//  https://github.com/slackhq/Keeper
// https://github.com/open-obfuscator/dProtect
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
 * @see fluxo.shrink.ShrinkerKeepRulesBySeedsTest
 *
 * @see com.android.tools.r8.R8
 * @see com.android.tools.r8.Version
 * @see proguard.ProGuard
 * @see proguard.ProGuard.getVersion
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun setupArtifactsProcessing(
    conf: FluxoConfigurationExtensionImpl,
) {
    val processorChains = conf.artifactProcessors.get()
    if (processorChains.isEmpty() || modeIsNotSupported(conf)) {
        return
    }

    val processArtifacts = conf.processArtifacts
    if (!processArtifacts && !isProcessingTaskCalled(conf)) {
        return
    }

    val p = conf.project
    p.logger.l("Setup artifacts processing (shrink, optimize, shadow, etc.)")

    val tasks = p.tasks
    val dependencies = mutableListOf<Any>("jar")
    val runAfter = mutableListOf<Any>()
    val chainTailTasks = mutableListOf<TaskProvider<*>>()
    val testTasks = mutableListOf<TaskProvider<*>>()

    // Auto-generate keep rules from API reports
    val isApplication = conf.isApplication
    val autoGenerateKeepRules = !isApplication &&
        conf.autoGenerateKeepRulesFromApis &&
        conf.enableApiValidation
    if (autoGenerateKeepRules) {
        val rulesGenRask = p.registerShrinkerKeepRulesGenTask(conf.autoGenerateKeepModifier)
        when {
            !processArtifacts -> dependencies += rulesGenRask
            else -> runAfter += rulesGenRask
        }
    }

    val mainClassesProvider = p.getMainClassesProvider()

    val testChainArtifact = !conf.ctx.testsDisabled
    var replaceOutgoingJar = conf.replaceOutgoingJar
    processorChains.forEachIndexed { chainId: Int, pc ->
        var stepId = 0
        var chainTailTask: TaskProvider<*>? = null
        var mainJar: Provider<RegularFile>? = null
        var inputFiles: FileCollection? = null
        var destinationDir: Provider<out Directory>? = null
        var chainState: ProcessorChainState? = null
        val chainDependencies = dependencies.toMutableList()
        val chainForLog = mutableListOf<JvmShrinker>()
        var config = pc as ArtifactProcessorConfigImpl?
        requireNotNull(config)
        while (config != null) {
            val next = config.next.orNull as ArtifactProcessorConfigImpl?
            when (val shrinker = config.processor) {
                is JvmShrinker -> {
                    // FIXME: Shrinkers loose JARs reproducibility,
                    //  repackage resulting JARs with proper settings
                    //  if reproducibility is required.

                    val hasNext = next != null
                    chainForLog += shrinker

                    val obfuscate = config.obfuscate.get()
                    val hasNextObfuscator =
                        hasNext && next?.obfuscateIncrementally?.orNull != false
                    if (obfuscate && hasNextObfuscator && !config.obfuscateIncrementally.get()) {
                        p.logger.w(
                            "'obfuscateIncrementally' is disabled for the shrinker $shrinker, " +
                                "but enabled for the next processor in the chain" +
                                " (${next?.processor}). It's probably a mistake!",
                        )
                    }

                    val setup = ProcessorSetup(
                        conf = conf,
                        processor = shrinker,
                        config = config,
                        chainId = chainId,
                        stepId = stepId,
                        dependencies = chainDependencies.toList(),
                        runAfter = runAfter,
                        chainState = chainState,
                        chainForLog = chainForLog.joinToString(separator = " => ") { it.name },
                        processAsApp = isApplication,
                    )

                    val shrinkTask = p.registerShrinkerTask(setup, mainClassesProvider)

                    if (hasNext || replaceOutgoingJar || testChainArtifact) {
                        mainJar = shrinkTask.flatMap { it.destinationFile }
                        destinationDir = shrinkTask.flatMap { it.destinationDir }

                        inputFiles = when {
                            // R8 Always provides only one output file.
                            // If more than one artifact is processed (e.g. app mode),
                            // it works as a shadowing (uber-jar).
                            shrinker === JvmShrinker.R8 && isApplication -> null

                            // FIXME: Support other shadowing/relocation processors in the chain.
                            else -> when (chainState) {
                                null -> p.objects.fileCollection().apply {
                                    from(shrinkTask.map { it.inputFiles })
                                }

                                else -> chainState.inputFiles
                            }
                        }
                    }
                    if (hasNext) {
                        chainDependencies += shrinkTask

                        val mappingFile = when {
                            obfuscate -> shrinkTask.flatMap { it.mappingFile }
                            else -> chainState?.mappingFile
                        }

                        chainState = ProcessorChainState(
                            mainJar = requireNotNull(mainJar),
                            inputFiles = inputFiles,
                            mappingFile = mappingFile,
                        )
                    } else {
                        chainTailTask = shrinkTask
                    }
                }
            }
            config = next
            stepId++
        }

        val chain = if (chainId == 0) "" else " #${chainId + 1}"
        p.logger.l("Artifact processing chain$chain: $chainForLog")
        chainTailTask?.let { chainTailTasks += it }

        // Replace the original artifact with processed one, but only once.
        // Replaces the default jar in outgoingVariants.
        // FIXME: Provide unporcessed artifacts alongside the processed ones as a variant.
        val jarProvider = requireNotNull(mainJar)
        val lastChainTask = chainTailTask
        if (replaceOutgoingJar) {
            replaceOutgoingJar = false
            if (!isApplication) {
                p.replaceOutgoingArtifactJarInConfigurations(
                    jarProvider = jarProvider,
                    builtBy = lastChainTask,
                )
            } else {
                val finalDestinationDir = requireNotNull(destinationDir)
                p.onComposeDesktopApplication { jvmApp ->
                    p.processComposeDesktopArtifact(
                        conf = conf,
                        jvmApp = jvmApp,
                        jarProvider = jarProvider,
                        destinationDir = finalDestinationDir,
                        builtBy = lastChainTask,
                    )
                }
            }
        }

        if (testChainArtifact && !isApplication && lastChainTask != null) {
            val verifyTask = tasks.register<ShrinkerVerificationTestTask>(
                name = getShrinkerVerifyTaskName(chainId = chainId),
            ) {
                dependsOn(lastChainTask)

                jar.set(jarProvider)
                this.inputFiles.setFrom(inputFiles)

                this.chainForLog.set(chainForLog.joinToString(separator = " => ") { it.name })

                val project = project
                val projectDir = project.layout.projectDirectory
                val apiFiles = project.fileTree(projectDir.dir(API_DIR)).matching {
                    include("**/*.api")
                }
                generatedDefinitions.setFrom(apiFiles)
            }
            p.run { verifyTask.bindToApiDumpTasks(optional = true) }
            chainTailTasks += verifyTask
            testTasks += verifyTask
        }
    }

    if (processArtifacts && chainTailTasks.isNotEmpty()) {
        tasks.named(CHECK_TASK_NAME) {
            dependsOn(chainTailTasks)
        }
    }
    if (testChainArtifact) {
        val bind = Action<Task> { dependsOn(testTasks) }
        tasks.named(TEST_TASK_NAME, bind)
        tasks.named(CHECK_TASK_NAME, bind)
    }
}

private fun isProcessingTaskCalled(conf: FluxoConfigurationExtensionImpl) =
    conf.ctx.startTaskNames.any {
        it.startsWith(PROCESS_TASK_PREFIX) || it == SHRINKER_KEEP_GEN_TASK_NAME
    }

private fun modeIsNotSupported(conf: FluxoConfigurationExtensionImpl) = when (conf.mode) {
    ConfigurationType.KOTLIN_MULTIPLATFORM -> true

    ConfigurationType.ANDROID_LIB,
    ConfigurationType.ANDROID_APP,
    -> true

    else -> false
}
