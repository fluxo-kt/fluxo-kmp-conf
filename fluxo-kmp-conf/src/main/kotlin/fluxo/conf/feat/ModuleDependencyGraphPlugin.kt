package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.MODULE_DEPENDENCY_GRAPH_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.MODULE_DEPENDENCY_GRAPH_PLUGIN_ID
import fluxo.conf.data.BuildConstants.MODULE_DEPENDENCY_GRAPH_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied

// `graphModules` task to create an image with the graph of how gradle modules depend on each other
// https://github.com/savvasdalkitsis/module-dependency-graph/releases
// https://plugins.gradle.org/plugin/com.savvasdalkitsis.module-dependency-graph
internal fun FluxoKmpConfContext.prepareModuleDependencyGraphPlugin() {
    if (hasAnyTaskCalled(MODULE_DEPENDENCY_GRAPH_TASK_NAME)) {
        loadAndApplyPluginIfNotApplied(
            id = MODULE_DEPENDENCY_GRAPH_PLUGIN_ID,
            className = MODULE_DEPENDENCY_GRAPH_CLASS_NAME,
            version = MODULE_DEPENDENCY_GRAPH_PLUGIN_VERSION,
            catalogPluginId = MODULE_DEPENDENCY_GRAPH_PLUGIN_ALIAS,
        )
    }
}

private const val MODULE_DEPENDENCY_GRAPH_TASK_NAME = "graphModules"

/** @see com.savvasdalkitsis.module.deps.graph.ModuleDependencyGraphPlugin */
private const val MODULE_DEPENDENCY_GRAPH_CLASS_NAME =
    "com.savvasdalkitsis.module.deps.graph.ModuleDependencyGraphPlugin"
