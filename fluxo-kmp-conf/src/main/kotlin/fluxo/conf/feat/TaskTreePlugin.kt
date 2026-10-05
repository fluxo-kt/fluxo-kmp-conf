package fluxo.conf.feat

import com.dorongold.gradle.tasktree.TaskTreePlugin
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.TASK_TREE_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.TASK_TREE_PLUGIN_ID
import fluxo.conf.data.BuildConstants.TASK_TREE_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.log.l

// Plugin that provides 'taskTree' task that prints the current task graph
// https://github.com/dorongold/gradle-task-tree
internal fun FluxoKmpConfContext.prepareTaskTreePlugin() {
    if (hasStartTaskCalled(TASK_TREE_TASK_NAME)) {
        rootProject.logger.l("prepareTaskTreePlugin, register :$TASK_TREE_TASK_NAME task")
        loadAndApplyPluginIfNotApplied(
            id = TASK_TREE_PLUGIN_ID,
            version = TASK_TREE_PLUGIN_VERSION,
            catalogPluginId = TASK_TREE_PLUGIN_ALIAS,
            fetchWithGradle = true,
        )
    }
}

private const val TASK_TREE_TASK_NAME = TaskTreePlugin.TASK_TREE_TASK_NAME

