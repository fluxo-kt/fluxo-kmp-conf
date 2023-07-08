package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.COMPLETE_KOTLIN_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.COMPLETE_KOTLIN_PLUGIN_ID
import fluxo.conf.data.BuildConstants.COMPLETE_KOTLIN_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.onBuildFinished
import loadKmmCodeCompletion

/**
 * Gradle Plugin that adds auto-completion
 * and symbol resolution for all Kotlin/Native platforms on any OS.
 *
 * Does project repo addition (incompatible with `RepositoriesMode.FAIL_ON_PROJECT_REPOS`)!
 * Downloads files that contain platform klibs intended for other OSes,
 * and puts them in the right directories.
 *
 * It only downloads the missing ones,
 * so it has no effect on macOS if you don't use Windows (mingw target)
 * or Linux MIPS targets, for example.
 *
 * Can be turned off once required libs are downloaded and saved.
 *
 * [See details](https://github.com/LouisCAD/CompleteKotlin/releases)
 */
internal fun FluxoKmpConfContext.prepareCompleteKotlinPlugin() {
    // https://github.com/LouisCAD/CompleteKotlin/releases
    if (!isCI && rootProject.loadKmmCodeCompletion()) {
        onProjectInSyncRun {
            val pluginId = COMPLETE_KOTLIN_PLUGIN_ID
            loadAndApplyPluginIfNotApplied(
                id = pluginId,
                className = COMPLETE_KOTLIN_CLASS_NAME,
                version = COMPLETE_KOTLIN_PLUGIN_VERSION,
                catalogPluginId = COMPLETE_KOTLIN_PLUGIN_ALIAS,
            )

            onBuildFinished {
                val flag = LOAD_KMM_CODE_COMPLETION_FLAG
                rootProject.logger.warn(
                    """

                    * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
                    '$flag' is enabled.
                    It enables the '$pluginId' Gradle plugin which downloads files with
                    platform klibs intended for other OSes, and puts them in the right directories
                    for auto-completion and symbol resolution for all Kotlin/Native platforms.

                    Don't forget to disable '$flag' once all required libs are downloaded and saved!
                    * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
                    """.trimIndent(),
                )
            }
        }
    }
}

/** @see com.louiscad.complete_kotlin.CompleteKotlinPlugin */
private const val COMPLETE_KOTLIN_CLASS_NAME = "com.louiscad.complete_kotlin.CompleteKotlinPlugin"

internal const val LOAD_KMM_CODE_COMPLETION_FLAG = "LOAD_KMM_CODE_COMPLETION"
