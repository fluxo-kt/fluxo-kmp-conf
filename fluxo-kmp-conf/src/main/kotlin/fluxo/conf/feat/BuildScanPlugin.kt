package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.e
import fluxo.conf.impl.l

// Build scans, data to speed up build and improve build reliability.
// https://scans.gradle.com/plugin/
// https://plugins.gradle.org/plugin/com.gradle.develocity
internal fun FluxoKmpConfContext.prepareBuildScanPlugin() {
    val project = rootProject
    try {
        if (project.hasProperty("buildScan")) {
            @Suppress("DEPRECATION")
            project.configureExtension<com.gradle.scan.plugin.BuildScanExtension>("develocity") {
                termsOfServiceUrl = "https://gradle.com/terms-of-service"
                termsOfServiceAgree = "yes"
                project.logger.l("Configured the build scan extension: ${javaClass.name}")
            }
        }
    } catch (e: Throwable) {
        if (SHOW_DEBUG_LOGS) {
            project.logger.e("Failed to configure the build scan plugin", e)
        }
    }
}
