package fluxo.conf.pub

import fluxo.conf.data.BuildConstants.GRADLE_PLUGIN_PUBLISH_PLUGIN_ID
import fluxo.conf.data.BuildConstants.GRADLE_PLUGIN_PUBLISH_PLUGIN_VERSION
import fluxo.conf.deps.reportMissingFromBuildClasspath
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.feat.setupValidatePluginTasks
import org.gradle.api.Project
import org.gradle.api.plugins.UnknownPluginException

/**
 * plugin-publish applies a helper plugin by id, so it works only from the module's own build
 * classpath, where the settings plugin or the root hook put it (`FluxoTool.kt`); fluxo can't
 * load it itself. Missing there (a root project, or a build that couldn't resolve it), the
 * module still builds, only without Plugin Portal publication, as before.
 */
internal fun Project.setupGradlePublishPlugin(conf: FluxoConfigurationExtensionImpl) {
    try {
        pluginManager.apply(GRADLE_PLUGIN_PUBLISH_PLUGIN_ID)
    } catch (_: UnknownPluginException) {
        if (conf.enablePublication == true) {
            reportMissingFromBuildClasspath(
                GRADLE_PLUGIN_PUBLISH_PLUGIN_ID,
                GRADLE_PLUGIN_PUBLISH_PLUGIN_VERSION,
                consequence = "Until then Plugin Portal publication is not set up.",
            )
        }
    }

    setupValidatePluginTasks(conf)
}
