package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.log.w

// Gradle Doctor: helps diagnose and fix common build problems.
// https://runningcode.github.io/gradle-doctor/
internal fun FluxoKmpConfContext.setupGradleDoctorPlugin() {
    val project = rootProject
    try {
        loadAndApplyPluginIfNotApplied(
            id = GRADLE_DOCTOR_PLUGIN_ID,
            className = GRADLE_DOCTOR_CLASS_NAME,
            catalogPluginIds = CATALOG_PLUGIN_IDS,
            catalogVersionIds = CATALOG_PLUGIN_IDS,
            canLoadDynamically = false,
            project = project,
        )
    } catch (e: Throwable) {
        project.logger.w("Failed to apply Gradle Doctor plugin: $e", e)
    }
}

private val CATALOG_PLUGIN_IDS = arrayOf(
    "gradle-doctor",
    "doctor",
    "osacky-doctor",
)

private const val GRADLE_DOCTOR_PLUGIN_ID = "com.osacky.doctor"
private const val GRADLE_DOCTOR_CLASS_NAME = "com.osacky.doctor.DoctorPlugin"
