package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.l
import fluxo.conf.impl.register
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.diagnostics.DependencyReportTask

internal fun FluxoKmpConfContext.prepareDependencyAnalysisTasks() {
    registerListDependenciesTasks()
    registerResolveDependenciesTasks()
}


// region allDeps

// Convenience task to print full dependencies tree for any module
// Use `buildEnvironment` task for the report about plugins
// https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html
private fun FluxoKmpConfContext.registerListDependenciesTasks() {
    val isCalled = hasStartTaskCalled(ALL_DEPS_TASK_NAME, ALL_DEPS_TASK_ALT_NAME)
    if (isCalled) {
        markProjectInSync()
        rootProject.subprojects { registerTaskAllDeps() }
    }
    onProjectInSyncRun(forceIf = isCalled) {
        rootProject.logger.l("register :$ALL_DEPS_TASK_NAME task")
        rootProject.registerTaskAllDeps()
    }
}

private fun Project.registerTaskAllDeps() {
    /**
     * @see org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer
     * @see org.gradle.internal.graph.GraphRenderer
     */
    val configuration = Action<DependencyReportTask> {
        group = TASK_GROUP_OTHER
        description = "Report full dependencies details for all configurations"
    }
    val clazz = DependencyReportTask::class.java
    tasks.register(ALL_DEPS_TASK_NAME, clazz, configuration)
    tasks.register(ALL_DEPS_TASK_ALT_NAME, clazz, configuration)
}

private const val ALL_DEPS_TASK_NAME = "allDeps"
private const val ALL_DEPS_TASK_ALT_NAME = "depsAll"

// endregion


// region resolveDependencies

private fun FluxoKmpConfContext.registerResolveDependenciesTasks() {
    onProjectInSyncRun(forceIf = hasStartTaskCalled(RESOLVE_DEPENDENCIES_TASK_NAME)) {
        val project = rootProject
        project.logger.l("register :$RESOLVE_DEPENDENCIES_TASK_NAME task")
        project.tasks.register<Task>(RESOLVE_DEPENDENCIES_TASK_NAME) {
            group = TASK_GROUP_OTHER
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


private const val TASK_GROUP_OTHER = "other"
