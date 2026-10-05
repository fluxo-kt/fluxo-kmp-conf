package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.TASK_INFO_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.TASK_INFO_PLUGIN_ID
import fluxo.conf.data.BuildConstants.TASK_INFO_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.log.l
import fluxo.log.w
import org.barfuin.gradle.taskinfo.GradleTaskInfoPlugin
import org.gradle.util.GradleVersion

// Provides task metadata and dependency information, execution queue, and more
// https://gitlab.com/barfuin/gradle-taskinfo/-/tags
// https://plugins.gradle.org/plugin/org.barfuin.gradle.taskinfo
internal fun FluxoKmpConfContext.prepareTaskInfoPlugin() {
    if (hasStartTaskCalled(TASK_INFO_TASK_NAMES)) {
        if (SHOW_DEBUG_LOGS) {
            check(TASK_INFO_PLUGIN_ID == GradleTaskInfoPlugin.PLUGIN_ID) {
                "TASK_INFO_PLUGIN_ID($TASK_INFO_PLUGIN_ID) != ${GradleTaskInfoPlugin.PLUGIN_ID}"
            }
        }
        if (!gradleHasTaskInfoApi()) {
            rootProject.logger.w(
                "taskinfo ($TASK_INFO_PLUGIN_VERSION, its newest release) can't run on Gradle " +
                    "${GradleVersion.current().version}: " +
                    "Gradle changed the internal API it reads. " +
                    "Use `taskTree`, or Gradle's own `--task-graph` (Gradle 9.1+).",
            )
            return
        }
        TASK_INFO_TASK_NAMES.joinToString(prefix = ":").let { tasks ->
            rootProject.logger.l("prepareTaskInfoPlugin, register tasks: $tasks")
        }
        loadAndApplyPluginIfNotApplied(
            id = GradleTaskInfoPlugin.PLUGIN_ID,
            version = TASK_INFO_PLUGIN_VERSION,
            catalogPluginId = TASK_INFO_PLUGIN_ALIAS,
            fetchWithGradle = true,
        )
    }
}

private val TASK_INFO_TASK_NAMES = arrayOf(
    GradleTaskInfoPlugin.TASKINFO_TASK_NAME,
    GradleTaskInfoPlugin.TASKINFO_JSON_TASK_NAME,
    GradleTaskInfoPlugin.TASKINFO_ORDERED_TASK_NAME,
)

/**
 * taskinfo 3.0.2 calls `QueryableExecutionPlan.getScheduledNodes()` returning its nested
 * `ScheduledNodes` type. Gradle 9.0 has that signature; 9.8 keeps only a `Set`-returning one, so
 * `tiTree` dies with `NoSuchMethodError` (the method NAME still exists, so the probe checks the
 * return type). An internal Gradle class: anything unexpected counts as missing.
 */
private fun gradleHasTaskInfoApi(): Boolean = try {
    Class.forName("org.gradle.execution.plan.QueryableExecutionPlan").methods.any {
        it.name == "getScheduledNodes" && it.returnType.simpleName == "ScheduledNodes"
    }
} catch (_: Throwable) {
    false
}
