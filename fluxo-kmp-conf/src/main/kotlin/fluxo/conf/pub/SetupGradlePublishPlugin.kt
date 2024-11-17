package fluxo.conf.pub

import fluxo.conf.data.BuildConstants.GRADLE_PLUGIN_PUBLISH_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.GRADLE_PLUGIN_PUBLISH_PLUGIN_ID
import fluxo.conf.data.BuildConstants.GRADLE_PLUGIN_PUBLISH_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.feat.setupValidatePluginTasks
import org.gradle.api.Project

internal fun Project.setupGradlePublishPlugin(conf: FluxoConfigurationExtensionImpl) {
    conf.ctx.loadAndApplyPluginIfNotApplied(
        id = GRADLE_PLUGIN_PUBLISH_PLUGIN_ID,
        version = GRADLE_PLUGIN_PUBLISH_PLUGIN_VERSION,
        catalogPluginId = GRADLE_PLUGIN_PUBLISH_PLUGIN_ALIAS,
        project = this,

        // Lookup will fail with AmbiguousGraphVariantsException
        // (shadowRuntimeElements variant is needed
        // but not resolved dynamically at this moment)
        lookupClassName = false,
//        className = GRADLE_PLUGIN_PUBLISH_CLASS_NAME,
    )

    setupValidatePluginTasks(conf)
}

@Suppress("unused")
// language=jvm-class-name
private const val GRADLE_PLUGIN_PUBLISH_CLASS_NAME = "com.gradle.publish.PublishPlugin"
