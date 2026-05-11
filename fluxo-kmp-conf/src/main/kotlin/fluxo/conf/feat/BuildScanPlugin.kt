package fluxo.conf.feat

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import fluxo.conf.FluxoKmpConfContext
import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.log.e
import fluxo.log.l
import fluxo.log.w

// Develocity: build scans, data to speed up build and improve build reliability.
// https://scans.gradle.com/plugin/
// https://plugins.gradle.org/plugin/com.gradle.develocity
internal fun FluxoKmpConfContext.prepareBuildScanPlugin(): Unit = rootProject.run {
    try {
        // New and shiny Develocity plugin.
        val develocity = extensions.findByName("develocity")
        if (develocity != null && develocity is DevelocityConfiguration) {
            develocity.buildScan {
                publishing.onlyIf { false }
                termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
                termsOfUseAgree.set("yes")
                logger.l("Configured the build scan plugin (develocity)")
            }
            return
        }

        // Compatibility with the old plugin.
        if (hasProperty("buildScan")) {
            return
        }
        val buildScan = extensions.findByName("buildScan")
        if (buildScan != null && configureLegacyBuildScan(buildScan)) {
            logger.w("Configured the build scan plugin (legacy gradle.enterprise)")
        }
    } catch (e: Throwable) {
        if (SHOW_DEBUG_LOGS) {
            logger.e("Failed to configure the build scan plugin", e)
        }
    }
}

private fun configureLegacyBuildScan(buildScan: Any): Boolean {
    val type = buildScan.javaClass
    val setUrl = type.methods.firstOrNull {
        it.name == "setTermsOfServiceUrl" && it.hasSingleStringParameter()
    }
    val setAgree = type.methods.firstOrNull {
        it.name == "setTermsOfServiceAgree" && it.hasSingleStringParameter()
    }
    if (setUrl != null && setAgree != null) {
        setUrl.invoke(buildScan, "https://gradle.com/terms-of-service")
        setAgree.invoke(buildScan, "yes")
    }
    return setUrl != null && setAgree != null
}

private fun java.lang.reflect.Method.hasSingleStringParameter(): Boolean =
    parameterTypes.size == 1 && parameterTypes[0] == String::class.java
