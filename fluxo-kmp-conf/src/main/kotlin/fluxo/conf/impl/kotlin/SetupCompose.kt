package fluxo.conf.impl.kotlin

import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.feat.CONFIG_DIR_NAME
import fluxo.conf.impl.configureExtension
import fluxo.gradle.ioFile
import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.log.d
import fluxo.log.e
import fluxo.log.w
import java.io.File
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation


@Suppress("LongMethod", "ReturnCount")
internal fun FluxoConfigurationExtensionImpl.setupCompose(): ComposeConfiguration? {
    val kc = kotlinConfig
    if (!kc.useKotlinCompose) {
        return null
    }

    val ctx = ctx
    val isRelease = ctx.isRelease
    val isMaxDebug = ctx.isMaxDebug
    val composeMetrics = ctx.composeMetricsEnabled || ctx.isCI || isRelease || isMaxDebug

    val project = project
    val reportsDir = when (composeMetrics) {
        true -> project.layout.buildDirectory.dir("reports/compose")
        else -> null
    }

    val rootProjectDir = project.rootProject.layout.projectDirectory
    val stabilityConfFile: File? = sequenceOf(
        project.layout.projectDirectory.file(COMPOSE_STABILITY_CONFIG_FILE_NAME).asFile,
    ).plus(CONFIG_FILE_NAMES.asSequence().map { rootProjectDir.file(it).asFile })
        .firstOrNull { it.exists() }

    try {
        if (!project.hasKotlinCompose) {
            ctx.loadAndApplyPluginIfNotApplied(
                id = KOTLIN_COMPOSE_PLUGIN_ID,
                className = KOTLIN_COMPOSE_PLUGIN_CLASS,
                version = ctx.kotlinPluginVersion.toString(),
                canLoadDynamically = false,
                project = project,
            )
        }

        // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compiler.html#compose-compiler-options-dsl
        // https://github.com/slackhq/slack-gradle-plugin/pull/641/files#diff-b837542982e1d33bf7f97cc53e00f53bbfd88a3c3c6bc91bdb23b734cfe2f0a5R852
        project.configureExtension<ComposeCompilerGradlePluginExtension>(
            name = "composeCompiler",
        ) {
            enableStrongSkippingMode.set(true)
            enableIntrinsicRemember.set(true)
            stabilityConfFile?.let { stabilityConfigurationFile.set(it) }
            reportsDir?.let {
                // TODO: Make Compose Compiler metrics in HTML automatically with Gradle.
                metricsDestination.set(it)
                reportsDestination.set(it)
            }
            if (!isRelease) {
                // Experimental options.
                enableNonSkippingGroupOptimization.set(true)

                if (isMaxDebug) {
                    includeSourceInformation.set(true)
                    generateFunctionKeyMetaClasses.set(true)
                    includeTraceMarkers.set(true)
                }
            }
        }
        if (SHOW_DEBUG_LOGS) {
            project.logger.d("Configured Kotlin Compose plugin.")
        }
        return null
    } catch (e: Throwable) {
        project.logger.e("Failed to configure Kotlin Compose plugin: $e", e)
    }

    // Fallback for the old configuration.
    project.logger.w(OLD_CONFIGURATION_WARNING)

    return ComposeConfiguration(
        stabilityConfFile = stabilityConfFile,
        reportsDir = reportsDir,
    )
}

internal class ComposeConfiguration(
    val stabilityConfFile: File?,
    val reportsDir: Provider<Directory>?,
)


internal fun KotlinCompilation<KotlinCommonOptions>.setupComposeLegacyWay(
    conf: FluxoConfigurationExtensionImpl,
    composeConf: ComposeConfiguration,
    isTest: Boolean,
) {
    // Compose options can work with errors using new `compilerOptions`.
    // Continue with `kotlinOptions` for now.
    val ko = kotlinOptions

    val p = "plugin:androidx.compose.compiler.plugins.kotlin"
    if (conf.suppressKotlinComposeCompatibilityCheck == true) {
        val v = conf.ctx.kotlinPluginVersion.toString()
        ko.freeCompilerArgs += listOf("-P", "$p:suppressKotlinVersionCompatibilityCheck=$v")
    }

    // https://developer.android.com/develop/ui/compose/performance/stability/fix#configuration-file
    composeConf.stabilityConfFile?.let {
        ko.freeCompilerArgs += listOf("-P", "$p:stabilityConfigurationPath=${it.absolutePath}")
    }

    val reportsDir = composeConf.reportsDir
    val composeMetrics = !isTest && reportsDir != null
    if (!composeMetrics || reportsDir == null) {
        return
    }

    // Output Compose Compiler metrics to the specified directory.
    // https://chris.banes.dev/composable-metrics/
    // https://github.com/androidx/androidx/blob/androidx-main/compose/compiler/design/compiler-metrics.md#interpreting-compose-compiler-metrics
    val rDir = reportsDir.ioFile.absolutePath
    ko.freeCompilerArgs += listOf("-P", "$p:metricsDestination=$rDir")
    ko.freeCompilerArgs += listOf("-P", "$p:reportsDestination=$rDir")

    @Suppress("MaxLineLength")
    // Note: convert the report to the human-readable HTML.
    // https://patilshreyas.github.io/compose-report-to-html/
    // TODO: Make Compose Compiler metrics in HTML automatically with Gradle.
    // $ composeReport2Html -app LW -overallStatsReport app_primaryDebug-module.json -detailedStatsMetrics app_primaryDebug-composables.csv -composableMetrics app_primaryDebug-composables.txt -classMetrics app_primaryDebug-classes.txt -o htmlReportDebug
    // $ composeReport2Html -app LW -overallStatsReport app_primaryRelease-module.json -detailedStatsMetrics app_primaryRelease-composables.csv -composableMetrics app_primaryRelease-composables.txt -classMetrics app_primaryRelease-classes.txt -o htmlReportRelease
    return
}


private const val COMPOSE_STABILITY_CONFIG_FILE_NAME = "compose.conf"

private val CONFIG_FILE_NAMES = arrayOf(
    "$CONFIG_DIR_NAME/$COMPOSE_STABILITY_CONFIG_FILE_NAME",
    "$CONFIG_DIR_NAME/compose/$COMPOSE_STABILITY_CONFIG_FILE_NAME",
    COMPOSE_STABILITY_CONFIG_FILE_NAME,
)

private const val KOTLIN_COMPOSE_PLUGIN_CLASS =
    "org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradleSubplugin"

private const val OLD_CONFIGURATION_WARNING =
    "Using the old configuration for Compose Compiler! Please update to the Kotlin Compose plugin."
