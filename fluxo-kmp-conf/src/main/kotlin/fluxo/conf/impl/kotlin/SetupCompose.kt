package fluxo.conf.impl.kotlin

import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.feat.CONFIG_DIR_NAME
import fluxo.conf.impl.configureExtension
import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.log.d
import java.io.File
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension


@Suppress("LongMethod")
internal fun FluxoConfigurationExtensionImpl.setupCompose() {
    val kc = kotlinConfig
    if (!kc.useKotlinCompose) {
        return
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
        // StrongSkipping and IntrinsicRemember are enabled by default since Kotlin 2.1;
        // explicitly setting them is redundant and emits deprecation warnings.
        stabilityConfFile?.let { file ->
            stabilityConfigurationFiles.add(project.layout.file(project.provider { file }))
        }
        reportsDir?.let {
            // TODO: Make Compose Compiler metrics in HTML automatically with Gradle.
            metricsDestination.set(it)
            reportsDestination.set(it)
        }
        if (!isRelease && isMaxDebug) {
            includeSourceInformation.set(true)
            includeTraceMarkers.set(true)
        }
    }
    if (SHOW_DEBUG_LOGS) {
        project.logger.d("Configured Kotlin Compose plugin.")
    }
}


private const val COMPOSE_STABILITY_CONFIG_FILE_NAME = "compose.conf"

private val CONFIG_FILE_NAMES = arrayOf(
    "$CONFIG_DIR_NAME/$COMPOSE_STABILITY_CONFIG_FILE_NAME",
    "$CONFIG_DIR_NAME/compose/$COMPOSE_STABILITY_CONFIG_FILE_NAME",
    COMPOSE_STABILITY_CONFIG_FILE_NAME,
)

private const val KOTLIN_COMPOSE_PLUGIN_CLASS =
    "org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradleSubplugin"
