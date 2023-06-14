package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.TASK_INFO_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.TASK_INFO_PLUGIN_ID
import fluxo.conf.data.BuildConstants.TASK_INFO_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import org.barfuin.gradle.taskinfo.GradleTaskInfoPlugin

// Provides task metadata and dependency information, execution queue, and more
// https://gitlab.com/barfuin/gradle-taskinfo/-/tags
// https://plugins.gradle.org/plugin/org.barfuin.gradle.taskinfo
internal fun FluxoKmpConfContext.prepareTaskInfoPlugin() {
    if (hasAnyTaskCalled(TASK_INFO_TASK_NAMES)) {
        if (SHOW_DEBUG_LOGS) {
            check(TASK_INFO_PLUGIN_ID == GradleTaskInfoPlugin.PLUGIN_ID) {
                "TASK_INFO_PLUGIN_ID($TASK_INFO_PLUGIN_ID) != ${GradleTaskInfoPlugin.PLUGIN_ID}"
            }
        }
        loadAndApplyPluginIfNotApplied(
            id = GradleTaskInfoPlugin.PLUGIN_ID,
            className = TASK_INFO_CLASS_NAME,
            version = TASK_INFO_PLUGIN_VERSION,
            catalogPluginId = TASK_INFO_PLUGIN_ALIAS,
        )
    }
}

private val TASK_INFO_TASK_NAMES = arrayOf(
    GradleTaskInfoPlugin.TASKINFO_TASK_NAME,
    GradleTaskInfoPlugin.TASKINFO_JSON_TASK_NAME,
    GradleTaskInfoPlugin.TASKINFO_ORDERED_TASK_NAME,
)

/** @see org.barfuin.gradle.taskinfo.GradleTaskInfoPlugin */
private const val TASK_INFO_CLASS_NAME = "org.barfuin.gradle.taskinfo.GradleTaskInfoPlugin"
