package fluxo.conf.feat

import com.dorongold.gradle.tasktree.TaskTreePlugin
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.TASK_TREE_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.TASK_TREE_PLUGIN_ID
import fluxo.conf.data.BuildConstants.TASK_TREE_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied

// Plugin that provides 'taskTree' task that prints the current task graph
// https://github.com/dorongold/gradle-task-tree
internal fun FluxoKmpConfContext.prepareTaskTreePlugin() {
    if (hasStartTaskCalled(TASK_TREE_TASK_NAME)) {
        loadAndApplyPluginIfNotApplied(
            id = TASK_TREE_PLUGIN_ID,
            className = TASK_TREE_CLASS_NAME,
            version = TASK_TREE_PLUGIN_VERSION,
            catalogPluginId = TASK_TREE_PLUGIN_ALIAS,
        )
    }
}

private const val TASK_TREE_TASK_NAME = TaskTreePlugin.TASK_TREE_TASK_NAME

/** @see com.dorongold.gradle.tasktree.TaskTreePlugin */
private const val TASK_TREE_CLASS_NAME = "com.dorongold.gradle.tasktree.TaskTreePlugin"
