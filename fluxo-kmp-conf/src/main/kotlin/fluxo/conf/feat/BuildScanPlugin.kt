package fluxo.conf.feat

import com.gradle.scan.plugin.BuildScanExtension
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.e
import fluxo.conf.impl.v

// Build scans, data to speed up build and improve build reliability.
// https://scans.gradle.com/plugin/
// https://plugins.gradle.org/plugin/com.gradle.enterprise
internal fun FluxoKmpConfContext.prepareBuildScanPlugin() {
    val project = rootProject
    try {
        if (project.hasProperty("buildScan")) {
            project.configureExtension<BuildScanExtension>("buildScan") {
                termsOfServiceUrl = "https://gradle.com/terms-of-service"
                termsOfServiceAgree = "yes"
                project.logger.v("Configured the build scan plugin")
            }
        }
    } catch (e: Throwable) {
        if (SHOW_DEBUG_LOGS) {
            project.logger.e("Failed to configure the build scan plugin", e)
        }
    }
}
