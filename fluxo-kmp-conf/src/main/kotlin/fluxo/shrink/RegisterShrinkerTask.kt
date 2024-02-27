package fluxo.shrink

import MAIN_SOURCE_SET_NAME
import fluxo.artifact.dsl.ProcessorConfigR8
import fluxo.artifact.proc.JvmShrinker
import fluxo.artifact.proc.PROCESS_TASK_PREFIX
import fluxo.artifact.proc.ProcessorSetup
import fluxo.conf.data.BuildConstants
import fluxo.conf.deps.detachedDependency
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.capitalizeAsciiOnly
import fluxo.conf.impl.get
import fluxo.conf.impl.kotlin.KOTLIN_JVM_PLUGIN_ID
import fluxo.conf.impl.kotlin.KOTLIN_MPP_PLUGIN_ID
import fluxo.conf.impl.kotlin.javaSourceSets
import fluxo.conf.impl.kotlin.mppExt
import fluxo.conf.impl.l
import fluxo.conf.impl.lc
import fluxo.conf.impl.logDependency
import fluxo.conf.impl.register
import fluxo.conf.impl.v
import fluxo.conf.jvm.JvmFiles
import fluxo.conf.jvm.JvmFilesProvider
import fluxo.gradle.ioFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal fun Project.registerShrinkerTask(
    setup: ProcessorSetup<JvmShrinker, ProcessorConfigR8>,
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
    dontoptimize.set(settings.optimize.map { !it })
    dontobfuscate.set(settings.obfuscate.map { !it })
    val incrementObfuscation = settings.obfuscateIncrementally.get()
    obfuscateIncrementally.set(incrementObfuscation)

    maxHeapSize.set(settings.maxHeapSize)
    callFallbackOrder.set(settings.callFallbackOrder)

    (conf.androidMinSdk as? Int)?.let { androidMinSdk.set(it) }
    conf.kotlinConfig.jvmTarget?.let { jvmTarget.set(it) }

    val state = setup.chainState
    if (state != null) {
        inputFiles.from(state.inputFiles)
        mainJar.set(state.mainJar)
        if (incrementObfuscation) {
            state.mappingFile?.let { applyMapping.set(it) }
        }
    } else {
        useClasspathFiles { files ->
            inputFiles.from(files.allJars)
            mainJar.set(files.mainJar)
        }
    }

    val defaultRulesFile = defaultRulesFile.ioFile
    val shrinkerSpecificConf = defaultRulesFile.parentFile.resolve(shrinker.name.lc())
    if (shrinkerSpecificConf.exists()) {
        configurationFiles.from(shrinkerSpecificConf)
    } else if (!defaultRulesFile.exists()) {
        writeDefaultRulesFile(defaultRulesFile)
    }
}


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
        project.logDependency(toolLc, notation)
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

private fun <T : Task> T.useClasspathFiles(fn: T.(JvmFiles) -> Unit) {
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
                useClasspathFiles(filesProvider, fn)
            }
        }
    } else if (project.pluginManager.hasPlugin(KOTLIN_JVM_PLUGIN_ID)) {
        val mainSourceSet = project.javaSourceSets[MAIN_SOURCE_SET_NAME]
        val filesProvider =
            JvmFilesProvider.FromGradleSourceSet(mainSourceSet)
        useClasspathFiles(filesProvider, fn)
    }
}

private fun <T : Task> T.useClasspathFiles(
    provider: JvmFilesProvider,
    fn: T.(JvmFiles) -> Unit,
) {
    logger.v("Using classpath files ${provider.javaClass.simpleName} for task $name...")
    provider.jvmCompileFiles(project)
        .configureUsageBy(this, fn)
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
