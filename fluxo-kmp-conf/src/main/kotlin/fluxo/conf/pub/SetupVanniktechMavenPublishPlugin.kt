package fluxo.conf.pub

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.log.w

// Set up Vanniktech's Maven Publish Plugin.
// It's much better than default publishing configuration of most plugins.
internal fun FluxoKmpConfContext.setupVanniktechMavenPublishPlugin(): Boolean {
    val project = rootProject
    return try {
        loadAndApplyPluginIfNotApplied(
            id = VANNIKTECH_MAVEN_PUBLISH_PLUGIN_ID,
            className = VANNIKTECH_MAVEN_PUBLISH_CLASS_NAME,
            catalogPluginIds = CATALOG_PLUGIN_IDS,
            catalogVersionIds = CATALOG_PLUGIN_IDS,
            canLoadDynamically = false,
            project = project,
        ).applied
    } catch (e: Throwable) {
        project.logger.w("Failed to apply Vanniktech's Maven Publish Plugin", e)
        false
    }
}

private val CATALOG_PLUGIN_IDS = arrayOf(
    "vanniktech",
    "vanniktech-publish",
    "vanniktech-maven-publish",
    "vanniktech-mvn-publish ",
)

internal const val VANNIKTECH_MAVEN_PUBLISH_PLUGIN_ID = "com.vanniktech.maven.publish"
internal const val VANNIKTECH_MAVEN_PUBLISH_BASE_PLUGIN_ID = "com.vanniktech.maven.publish.base"

// language=jvm-class-name
private const val VANNIKTECH_MAVEN_PUBLISH_CLASS_NAME =
    "com.vanniktech.maven.publish.MavenPublishPlugin"

