@file:Suppress("CyclomaticComplexMethod")

package fluxo.shrink

import MAIN_SOURCE_SET_NAME
import fluxo.artifact.dsl.ProcessorConfigR8
import fluxo.artifact.proc.JvmShrinker
import fluxo.artifact.proc.PROCESS_TASK_PREFIX
import fluxo.artifact.proc.ProcessorSetup
import fluxo.conf.data.BuildConstants
import fluxo.conf.deps.detachedDependency
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.capitalizeAsciiOnly
import fluxo.conf.impl.get
import fluxo.conf.impl.kotlin.KOTLIN_JVM_PLUGIN_ID
import fluxo.conf.impl.kotlin.KOTLIN_MPP_PLUGIN_ID
import fluxo.conf.impl.kotlin.javaSourceSets
import fluxo.conf.impl.kotlin.mppExt
import fluxo.conf.impl.lc
import fluxo.conf.impl.logDependency
import fluxo.conf.impl.register
import fluxo.conf.jvm.JvmFiles
import fluxo.conf.jvm.JvmFilesProvider
import fluxo.gradle.ioFile
import fluxo.gradle.not
import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.log.l
import fluxo.log.v
import fluxo.log.w
import fluxo.vc.l
import fluxo.vc.v
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal const val PRO_EXT = ".pro"

/**
 *
 * @see org.jetbrains.compose.desktop.application.internal.configureProguardTask
 * @see org.jetbrains.compose.desktop.application.internal.configurePackagingTasks
 */
internal fun Project.registerShrinkerTask(
    setup: ProcessorSetup<JvmShrinker, ProcessorConfigR8>,
    mainClassesProvider: Provider<out Iterable<String>>,
): TaskProvider<AbstractShrinkerTask> = tasks.register<AbstractShrinkerTask>(
    name = setup.getShrinkerTaskName(),
) {
    val isVerbose = SHOW_DEBUG_LOGS || logger.isInfoEnabled
    if (isVerbose) {
        verbose.set(true)
    }

    setup.dependencies?.let { dependsOn(it) }
    setup.runAfter?.let { mustRunAfter(it) }

    val shrinker = setup.processor
    val settings = setup.config
    this.shrinker.set(shrinker)
    toolName.set(setup.getShrinkerToolName())
    setup.chainForLog?.let { chainForLog.set(it) }
    if (shrinker === JvmShrinker.R8) {
        r8FulMode.set(settings.fullMode)
    }

    val conf = setup.conf
    configureShrinkerMavenCoordinates(conf, isVerbose = isVerbose, shrinker)

    configurationFiles.from(settings.configurationFiles)

    // ProGuard uses -dontobfuscate option to turn off obfuscation, which is enabled by default
    // Disable obfuscation by default as often suboptimal with much harder troubleshooting.
    // If disabled by default, cleaner API uses flag name without a negation.
    // That's why a task property follows ProGuard design, when DSL does the opposite.
    dontoptimize.set(!settings.optimize)
    dontobfuscate.set(!settings.obfuscate)
    val incrementObfuscation = settings.obfuscateIncrementally.get()
    obfuscateIncrementally.set(incrementObfuscation)
    processAsApp.set(setup.processAsApp)

    maxHeapSize.set(settings.maxHeapSize)
    callFallbackOrder.set(settings.callFallbackOrder)

    (conf.androidMinSdk as? Int)?.let { androidMinSdk.set(it) }
    conf.kotlinConfig.jvmTarget?.let { jvmTarget.set(it) }

    mainClasses.set(mainClassesProvider)

    val state = setup.chainState
    if (state != null) {
        mainJar.set(state.mainJar)
        state.inputFiles?.let { inputFiles.from(it) }
        if (incrementObfuscation) {
            state.mappingFile?.let { applyMapping.set(it) }
        }
    } else {
        useClasspathFiles(useRuntimeClasspath = setup.processAsApp) { files ->
            inputFiles.from(files.allJars)
            mainJar.set(files.mainJar)
        }
    }

    val defaultRulesFile = defaultRulesFile.ioFile
    val shrinkerSpecificConf = defaultRulesFile.parentFile
        .resolve(shrinker.name.lc() + PRO_EXT)
    if (shrinkerSpecificConf.exists()) {
        configurationFiles.from(shrinkerSpecificConf)
    } else if (!defaultRulesFile.exists()) {
        writeDefaultRulesFile(defaultRulesFile)
    }
}


@Suppress("NestedBlockDepth", "LongMethod")
private fun AbstractShrinkerTask.configureShrinkerMavenCoordinates(
    conf: FluxoConfigurationExtensionImpl,
    isVerbose: Boolean,
    shrinker: JvmShrinker,
) {
    val toolLc = shrinker.name.lc()
    val project = project

    var coords = when (shrinker) {
        JvmShrinker.ProGuard -> BuildConstants.PROGUARD_PLUGIN
        JvmShrinker.R8 -> BuildConstants.R8
    }
    val libs = conf.ctx.libs
    var version = libs.v(toolLc) ?: libs.l(toolLc)?.version
    val parts = coords.split(':', limit = 3)
    @Suppress("MagicNumber")
    if (parts.size == 3) {
        val prev = parts[2]
        when (version) {
            null -> version = prev
            else -> {
                if (version != prev) {
                    project.logger.l(
                        "Override $shrinker version by libs.versions.toml " +
                            "to $version from $prev",
                    )
                }
                coords = "${parts[0]}:${parts[1]}:$version"
            }
        }
    } else if (version != null) {
        coords += ":$version"
    }
    if (version == null) {
        version = parts.getOrNull(2)
    }

    var bundledIsPreferred = false
    if (!version.isNullOrEmpty()) {
        var message = "Remote $shrinker version is $version"
        val bundledR8 = BUNDLED_R8_VERSION
        if (shrinker === JvmShrinker.R8 && bundledR8 != null && version != bundledR8) {
            try {
                val v = GradleVersion.version(version.substringBefore('-'))
                val b = GradleVersion.version(bundledR8.substringBefore('-'))
                if (v < b) {
                    message += " (bundled R8 is preferred: $bundledR8)"
                    PREFER_BUNDLED_R8.set(true)
                    bundledIsPreferred = true
                }
            } catch (e: Throwable) {
                project.logger.w("Invalid $shrinker version $version? $e")
            }
        }
        project.logger.v(message)
        REMOTE_SHRINKER_VERSION[shrinker] = version
    }
    if (!isVerbose) {
        notifyThatToolIsStarting(shrinker.name, version)
    }

    // For ProGuard, we want to upgrade to the latest version of the core library.
    when (shrinker) {
        JvmShrinker.ProGuard -> arrayOf(
            coords,
            BuildConstants.PROGUARD_CORE,
            BuildConstants.KOTLINX_METADATA_JVM,
        )

        else -> arrayOf(coords)
    }.onEach { notation ->
        // FIXME: Avoid duplicate logging between similar tasks
        // TODO: note dependency in the (root?) project classpath
        if (!bundledIsPreferred) {
            project.logDependency(toolLc, notation)
        }
    }.let {
        toolCoordinates.set(it.joinToString())
        toolJars.from(project.detachedDependency(it))
    }
}

private fun DefaultTask.notifyThatToolIsStarting(tool: String, version: String? = null) {
    doFirst {
        val v = if (version != null) " v$version" else ""
        logger.lifecycle("$tool$v starting ...")
    }
}

private fun <T : Task> T.useClasspathFiles(
    useRuntimeClasspath: Boolean = false,
    fn: T.(JvmFiles) -> Unit,
) {
    val project = project
    if (project.pluginManager.hasPlugin(KOTLIN_MPP_PLUGIN_ID)) {
        var isJvmTargetConfigured = false
        project.mppExt.targets.configureEach {
            if (platformType == KotlinPlatformType.jvm) {
                if (isJvmTargetConfigured) {
                    val error = "Default JVM configuration is not derminable: " +
                        "multiple Kotlin JVM targets definitions are detected. " +
                        "Specify, which target to use."
                    throw GradleException(error)
                }
                isJvmTargetConfigured = true

                val from = this
                check(from is KotlinJvmTarget) {
                    val fromName = from.javaClass.canonicalName
                    "Non JVM Kotlin MPP targets are not supported: $fromName " +
                        "is not subtype of ${KotlinJvmTarget::class.java.canonicalName}"
                }
                val filesProvider =
                    JvmFilesProvider.FromKotlinMppTarget(from)
                useClasspathFiles(filesProvider, fn, useRuntimeClasspath)
            }
        }
    } else if (project.pluginManager.hasPlugin(KOTLIN_JVM_PLUGIN_ID)) {
        val mainSourceSet = project.javaSourceSets[MAIN_SOURCE_SET_NAME]
        val filesProvider =
            JvmFilesProvider.FromGradleSourceSet(mainSourceSet)
        useClasspathFiles(filesProvider, fn, useRuntimeClasspath)
    }
}

private fun <T : Task> T.useClasspathFiles(
    provider: JvmFilesProvider,
    fn: T.(JvmFiles) -> Unit,
    useRuntimeClasspath: Boolean,
) {
    val type = if (useRuntimeClasspath) "runtime" else "compile"
    logger.v("Using $type classpath files ${provider.javaClass.simpleName} for task $name...")
    when {
        useRuntimeClasspath -> provider.jvmRuntimeFiles(project)
        else -> provider.jvmCompileFiles(project)
    }.configureUsageBy(this, fn)
}


private fun ProcessorSetup<JvmShrinker, *>.getShrinkerTaskName(): String =
    buildString(capacity = 32) {
        // processChainProguardJar
        // processChainR8Jar
        // processChainStep2R8Jar
        // processChain2Step3ProguardJar

        append(PROCESS_TASK_PREFIX)
        if (chainId != 0) {
            append(chainId + 1)
        }
        if (stepId != 0 || chainId != 0) {
            append("Step")
            append(stepId + 1)
        }
        append(processor.name.lc().capitalizeAsciiOnly())
        append("Jar")
    }

internal fun getShrinkerVerifyTaskName(chainId: Int): String =
    buildString(capacity = 32) {
        // processChainApiVerifyTest
        // processChain2ApiVerifyTest

        append(PROCESS_TASK_PREFIX)
        if (chainId != 0) {
            append(chainId + 1)
        }
        append("ApiVerifyTest")
    }

private fun ProcessorSetup<JvmShrinker, *>.getShrinkerToolName(): String =
    buildString(capacity = 16) {
        // proguard
        // r8
        // r8-2
        // proguard_2-3

        append(processor.name.lc())
        if (chainId != 0) {
            append('_')
            append(chainId + 1)
        }
        if (stepId != 0 || chainId != 0) {
            append('-')
            append(stepId + 1)
        }
    }
