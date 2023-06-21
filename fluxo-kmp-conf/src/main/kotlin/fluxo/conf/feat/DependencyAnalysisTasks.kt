package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.register
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.diagnostics.DependencyReportTask

internal fun FluxoKmpConfContext.prepareDependencyAnalysisTasks() {
    registerAllDepsTasks()
    registerResolveDependenciesTasks()
}


// region allDeps

// Convenience task to print full dependencies tree for any module
// Use `buildEnvironment` task for the report about plugins
// https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html
private fun FluxoKmpConfContext.registerAllDepsTasks() {
    val isCalled = hasAnyTaskCalled(ALL_DEPS_TASK_NAME)
    if (isCalled) {
        markProjectInSync()
        rootProject.subprojects { registerTaskAllDeps() }
    }
    onProjectInSyncRun(forceIf = isCalled) {
        rootProject.registerTaskAllDeps()
    }
}

private fun Project.registerTaskAllDeps() {
    /**
     * @see org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer
     * @see org.gradle.internal.graph.GraphRenderer
     */
    tasks.register<DependencyReportTask>(ALL_DEPS_TASK_NAME) {
        group = TASK_GROUP_NAME
        description = "Report full dependencies details for all configurations"
    }
}

private const val ALL_DEPS_TASK_NAME = "allDeps"

// endregion


// region resolveDependencies

private fun FluxoKmpConfContext.registerResolveDependenciesTasks() {
    onProjectInSyncRun(forceIf = hasAnyTaskCalled(RESOLVE_DEPENDENCIES_TASK_NAME)) {
        rootProject.tasks.register<Task>(RESOLVE_DEPENDENCIES_TASK_NAME) {
            group = TASK_GROUP_NAME
            description = "Resolve and prefetch dependencies"
            doLast {
                project.allprojects.forEach { p ->
                    p.configurations.plus(p.buildscript.configurations)
                        .filter { it.isCanBeResolved }
                        .forEach {
                            try {
                                it.resolve()
                            } catch (_: Throwable) {
                            }
                        }
                }
            }
        }
    }
}

private const val RESOLVE_DEPENDENCIES_TASK_NAME = "resolveDependencies"

// endregion


private const val TASK_GROUP_NAME = "other"
