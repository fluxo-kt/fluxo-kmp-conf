package fluxo.minification

import MAIN_SOURCE_SET_NAME
import fluxo.conf.data.BuildConstants
import fluxo.conf.data.BuildConstants.PROGUARD_CORE
import fluxo.conf.data.BuildConstants.PROGUARD_PLUGIN
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
import fluxo.minification.Shrinker.ProGuard
import fluxo.minification.Shrinker.R8
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal fun Project.registerShrinkerTask(
    conf: FluxoConfigurationExtensionImpl,
    parents: List<Any>? = null,
    runAfter: List<Any>? = null,
    forceShrinker: Shrinker? = null,
) = tasks.register<AbstractShrinkerTask>(
    name = getShrinkerTaskName(conf, forceShrinker),
) {
    val isVerbose = SHOW_DEBUG_LOGS || logger.isInfoEnabled
    if (isVerbose) {
        verbose.set(true)
    }

    parents?.let { dependsOn(it) }
    runAfter?.let { mustRunAfter(it) }

    val settings = conf.shrinkingConfig

    val shrinker = shrinker(forceShrinker, settings)
    this.shrinker.set(shrinker)

    r8FulMode.set(settings.r8FullMode.orNull == true)

    // TODO: Support R8 or ProgGuard available in the classpath (bundled)
    //  + notifyThatToolIsRunning
    //  https://github.com/tuuzed/LightTunnel/blob/680d3bc/buildSrc/src/main/kotlin/Compiler.kt

    configureShrinkerMavenCoordinates(conf, isVerbose = isVerbose, shrinker)

    configurationFiles.from(settings.configurationFiles)

    // ProGuard uses -dontobfuscate option to turn off obfuscation, which is enabled by default
    // Disable obfuscation by default as often suboptimal with much harder troubleshooting.
    // If disabled by default, cleaner API uses flag name without a negation.
    // That's why a task property follows ProGuard design, when DSL does the opposite.
    dontobfuscate.set(settings.obfuscate.map { !it })
    dontoptimize.set(settings.optimize.map { !it })

    maxHeapSize.set(settings.maxHeapSize)

    (conf.androidMinSdk as? Int)?.let { androidMinSdk.set(it) }
    conf.kotlinConfig.jvmTarget?.let { jvmTarget.set(it) }

    useClasspathFiles { files ->
        inputFiles.from(files.allJars)
        mainJar.set(files.mainJar)
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
    shrinker: Shrinker,
) {
    val toolLc = shrinker.name.lc()
    val project = project

    var coords = when (shrinker) {
        ProGuard -> PROGUARD_PLUGIN
        R8 -> BuildConstants.R8
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
        notifyThatToolIsRunning(shrinker.name, version)
    }

    // For ProGuard, we want to upgrade to the latest version of the core library.
    when (shrinker) {
        ProGuard -> arrayOf(coords, PROGUARD_CORE)
        else -> arrayOf(coords)
    }.onEach { notation ->
        project.logDependency(toolLc, notation)
    }.let {
        toolCoordinates.set(it.joinToString())
        toolJars.from(project.detachedDependency(it))
    }
}

private fun DefaultTask.notifyThatToolIsRunning(tool: String, version: String? = null) {
    doFirst {
        val v = if (version != null) " v$version" else ""
        logger.lifecycle("$tool$v running ...")
    }
}

private fun AbstractShrinkerTask.useClasspathFiles(
    fn: AbstractShrinkerTask.(JvmFiles) -> Unit,
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

private fun AbstractShrinkerTask.useClasspathFiles(
    provider: JvmFilesProvider,
    fn: AbstractShrinkerTask.(JvmFiles) -> Unit,
) {
    provider.jvmCompileFiles(project)
        .configureUsageBy(this, fn)
}


internal enum class Shrinker {
    ProGuard,
    R8,
}

private fun shrinker(
    forceShrinker: Shrinker?,
    settings: FluxoShrinkerConfig,
) = forceShrinker ?: if (settings.useR8.get()) R8 else ProGuard

// shrinkWithProguardJar
// shrinkWithR8Jar
private fun getShrinkerTaskName(
    conf: FluxoConfigurationExtensionImpl,
    forceShrinker: Shrinker? = null,
): String {
    val shrinker = shrinker(forceShrinker, conf.shrinkingConfig)
    return SHRINKER_TASK_PREFIX + shrinker.name.lc().capitalizeAsciiOnly() + "Jar"
}
