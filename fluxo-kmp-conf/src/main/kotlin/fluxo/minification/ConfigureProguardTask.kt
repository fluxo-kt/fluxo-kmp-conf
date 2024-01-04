package fluxo.minification

import MAIN_SOURCE_SET_NAME
import fluxo.conf.data.BuildConstants.PROGUARD_CORE
import fluxo.conf.data.BuildConstants.PROGUARD_PLUGIN
import fluxo.conf.deps.detachedDependency
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.get
import fluxo.conf.impl.kotlin.KOTLIN_JVM_PLUGIN_ID
import fluxo.conf.impl.kotlin.KOTLIN_MPP_PLUGIN_ID
import fluxo.conf.impl.kotlin.javaSourceSets
import fluxo.conf.impl.kotlin.mppExt
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
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal fun Project.registerProguardTask(
    conf: FluxoConfigurationExtensionImpl,
    parents: List<Any>? = null,
    runAfter: List<Any>? = null,
) = tasks.register<AbstractShrinkerTask>(PRO_GUARD_TASK_NAME) {
    val isVerbose = logger.isInfoEnabled
    if (isVerbose) {
        verbose.set(true)
    }

    parents?.let { dependsOn(it) }
    runAfter?.let { mustRunAfter(it) }

    toolName.set(TOOL_NAME)

    configureProguardMavenCoordinates(conf, isVerbose)

    val settings = conf.shrinkingConfig
    configurationFiles.from(settings.configurationFiles)

    // ProGuard uses -dontobfuscate option to turn off obfuscation, which is enabled by default
    // We want to disable obfuscation by default, because often
    // it is not needed, but makes troubleshooting much harder.
    // If obfuscation is turned off by default,
    // enabling (`isObfuscationEnabled.set(true)`) seems much better
    // than disabling obfuscation disabling (`dontObfuscate.set(false)`).
    // That's why a task property follows ProGuard design,
    // when our DSL does the opposite.
    dontobfuscate.set(settings.obfuscate.map { !it })
    dontoptimize.set(settings.optimize.map { !it })

    maxHeapSize.set(settings.maxHeapSize)

    conf.kotlinConfig.jvmTarget?.let { jvmTarget.set(it) }

    useClasspathFiles { files ->
        inputFiles.from(files.allRuntimeJars)
        mainJar.set(files.mainJar)
    }

    val defaultRulesFile = defaultRulesFile.ioFile
    if (!defaultRulesFile.exists()) {
        writeDefaultRulesFile(defaultRulesFile)
    }
}

private fun AbstractShrinkerTask.configureProguardMavenCoordinates(
    conf: FluxoConfigurationExtensionImpl,
    isVerbose: Boolean,
) {
    val tool = TOOL_NAME
    val toolLc = tool.lc()
    val project = project
    val coords = PROGUARD_PLUGIN.let {
        var coords = it
        var version = conf.ctx.libs.v(toolLc)
        val parts = it.split(':', limit = 3)
        @Suppress("MagicNumber")
        if (parts.size == 3) {
            when (version) {
                null -> version = parts[2]
                else -> coords = "${parts[0]}:${parts[1]}:$version"
            }
        } else if (version != null) {
            coords += ":$version"
        }
        if (!isVerbose) {
            notifyThatToolIsRunning(tool, version)
        }
        arrayOf(coords, PROGUARD_CORE)
    }.onEach { notation ->
        project.logDependency(toolLc, notation)
    }
    toolCoordinates.set(coords.joinToString())
    toolJars.from(project.detachedDependency(coords))
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

private const val TOOL_NAME = "ProGuard"

// shrinkWithProguardJar
private const val PRO_GUARD_TASK_NAME = SHRINKER_TASK_PREFIX + "ProguardJar"

// TODO: ProGuard/R8 configuration improvements
//  https://github.com/Guardsquare/proguard/tree/8afa59e/gradle-plugin/src/main
//  https://github.com/JetBrains/kotlin/blob/0926eba/libraries/tools/kotlin-main-kts/build.gradle.kts#L84
//  https://github.com/JetBrains/compose-multiplatform/blob/50d45f3/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/internal/configureJvmApplication.kt#L241
//  https://github.com/JetBrains/compose-multiplatform/blob/b67dde7/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/tasks/AbstractProguardTask.kt#L22
//  https://github.com/TWiStErRob/net.twisterrob.inventory/blob/cc4eb02/gradle/plugins-inventory/src/main/kotlin/net/twisterrob/inventory/build/unfuscation/UnfuscateTask.kt#L33

// TODO: dProtect obfuscator
//  https://github.com/open-obfuscator/dProtect
