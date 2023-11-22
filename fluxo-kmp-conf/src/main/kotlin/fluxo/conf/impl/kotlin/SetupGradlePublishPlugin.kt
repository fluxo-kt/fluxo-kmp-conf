package fluxo.conf.impl.kotlin

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.GRADLE_PLUGIN_PUBLISH_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.GRADLE_PLUGIN_PUBLISH_PLUGIN_ID
import fluxo.conf.data.BuildConstants.GRADLE_PLUGIN_PUBLISH_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import org.gradle.api.Project

internal fun Project.setupGradlePublishPlugin(context: FluxoKmpConfContext) {
    context.loadAndApplyPluginIfNotApplied(
        id = GRADLE_PLUGIN_PUBLISH_PLUGIN_ID,
        version = GRADLE_PLUGIN_PUBLISH_PLUGIN_VERSION,
        catalogPluginId = GRADLE_PLUGIN_PUBLISH_PLUGIN_ALIAS,
        project = this,

        // Lookup will fail with AmbiguousGraphVariantsException
        // (shadowRuntimeElements variant is needed, but not resolved dynamically at this moment)
        lookupClassName = false,
//        className = GRADLE_PLUGIN_PUBLISH_CLASS_NAME,
    )
}

@Suppress("unused")
private const val GRADLE_PLUGIN_PUBLISH_CLASS_NAME = "com.gradle.publish.PublishPlugin"
