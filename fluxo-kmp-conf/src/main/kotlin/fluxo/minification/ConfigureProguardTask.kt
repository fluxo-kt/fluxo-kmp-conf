package fluxo.minification

import MAIN_SOURCE_SET_NAME
import fluxo.conf.data.BuildConstants.PROGUARD_CORE
import fluxo.conf.data.BuildConstants.PROGUARD_PLUGIN
import fluxo.conf.deps.detachedDependency
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.get
import fluxo.conf.impl.kotlin.KOTLIN_JVM_PLUGIN_ID
import fluxo.conf.impl.kotlin.KOTLIN_MPP_PLUGIN_ID
import fluxo.conf.impl.kotlin.javaSourceSets
import fluxo.conf.impl.kotlin.mppExt
import fluxo.conf.impl.register
import fluxo.conf.jvm.JvmFiles
import fluxo.conf.jvm.JvmFilesProvider
import fluxo.gradle.ioFile
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal fun Project.registerProguardTask(
    conf: FluxoConfigurationExtensionImpl,
    parents: Array<out Any>? = null,
) = tasks.register<AbstractMinificationTask>(PRO_GUARD_TASK_NAME) {
    configureProguardTask(conf, parents)
}

private fun AbstractMinificationTask.configureProguardTask(
    conf: FluxoConfigurationExtensionImpl,
    parents: Array<out Any>?,
) {
    if (SHOW_DEBUG_LOGS) {
        verbose.set(true)
    }

    parents?.let { dependsOn(*it) }

    val tool = "ProGuard"
    toolName.set(tool)

    // TODO: Allow to configure ProGuard version
    val project = project
    val coords = arrayOf(PROGUARD_PLUGIN, PROGUARD_CORE)
    toolCoordinates.set(coords.joinToString())
    toolJars.from(project.detachedDependency(*coords))

    val settings = conf.minificationConfig
    configurationFiles.from(settings.configurationFiles)

    // ProGuard uses -dontobfuscate option to turn off obfuscation, which is enabled by default
    // We want to disable obfuscation by default, because often
    // it is not needed, but makes troubleshooting much harder.
    // If obfuscation is turned off by default,
    // enabling (`isObfuscationEnabled.set(true)`) seems much better,
    // than disabling obfuscation disabling (`dontObfuscate.set(false)`).
    // That's why a task property is follows ProGuard design,
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

private fun AbstractMinificationTask.useClasspathFiles(
    fn: AbstractMinificationTask.(JvmFiles) -> Unit,
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

private fun AbstractMinificationTask.useClasspathFiles(
    provider: JvmFilesProvider,
    fn: AbstractMinificationTask.(JvmFiles) -> Unit,
) {
    provider.jvmCompileFiles(project)
        .configureUsageBy(this, fn)
}


private const val PRO_GUARD_TASK_NAME = "minifyWithProguardJar"

// https://github.com/Guardsquare/proguard/tree/8afa59e/gradle-plugin/src/main
// https://github.com/JetBrains/kotlin/blob/0926eba/libraries/tools/kotlin-main-kts/build.gradle.kts#L84
// https://github.com/JetBrains/compose-multiplatform/blob/50d45f3/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/internal/configureJvmApplication.kt#L241
// https://github.com/JetBrains/compose-multiplatform/blob/b67dde7/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/tasks/AbstractProguardTask.kt#L22
// https://github.com/TWiStErRob/net.twisterrob.inventory/blob/cc4eb02/gradle/plugins-inventory/src/main/kotlin/net/twisterrob/inventory/build/unfuscation/UnfuscateTask.kt#L33

// TODO: dProtect obfuscator
//  https://github.com/open-obfuscator/dProtect
