package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.d
import fluxo.conf.impl.l
import fluxo.conf.impl.v
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.logging.Logger


private val unreachableTasksDisabled = AtomicBoolean(false)

// Disable unreachable tasks
// FIXME: Check if this is actually useful
internal fun FluxoKmpConfContext.ensureUnreachableTasksDisabled() {
    if (!unreachableTasksDisabled.compareAndSet(false, true)) {
        return
    }

    // Run only for CI.
    // Takes time and not so useful during local development.
    if (!(isCI || SHOW_DEBUG_LOGS) || isProjectInSyncRun) {
        return
    }

    val project = rootProject
    val logger = project.logger
    project.gradle.taskGraph.whenReady {
        if (!isProjectInSyncRun) {
            DisableUnreachableTasks(graph = this, logger = logger)
                .apply()
        }
    }
}

private class DisableUnreachableTasks(
    private val graph: TaskExecutionGraph,
    private val logger: Logger,
) {
    private val rootTasks = findRootTasks()
    private val results = HashMap<Pair<Task, Task>, Boolean>()

    private fun findRootTasks(): List<Task> {
        val rootTasks = ArrayList<Task>()

        val children = HashSet<Task>()
        graph.allTasks.forEach {
            children += graph.getDependencies(it)
        }

        graph.allTasks.forEach {
            if (it !in children) {
                rootTasks += it
            }
        }

        return rootTasks
    }

    fun apply() {
        if (SHOW_DEBUG_LOGS) logger.v("DisableUnreachableTasks.apply")
        for (it in graph.allTasks) {
            if (it.enabled) {
                disableChildren(it)
            }
        }
    }

    private fun disableChildren(task: Task) {
        graph.getDependencies(task).forEach { child ->
            if (child.enabled) {
                if (!isTaskAccessible(task = child)) {
                    child.enabled = false
                    logger.l("Inaccessible task disabled: ${child.path}")
                    disableChildren(task = child)
                } else {
                    logger.d("Task accessible: ${child.path}")
                }
            } else {
                logger.d("Task already disabled: ${child.path}")
                disableChildren(task = child)
            }
        }
    }

    private fun isTaskAccessible(task: Task): Boolean = rootTasks.any {
        val isPathExists = (it != task) && isPathExists(source = it, destination = task)

        if (isPathExists) {
            logger.d("Task ${task.path} accessible from ${it.path}")
        }

        isPathExists
    }

    private fun isPathExists(source: Task, destination: Task): Boolean =
        results.getOrPut(source to destination) {
            when {
                !source.enabled -> false
                source == destination -> true.also { logger.d("Task reached: ${destination.path}") }

                else -> graph.getDependencies(source)
                    .any { isPathExists(source = it, destination = destination) }
                    .also {
                        if (it) {
                            logger.d("Task path found from ${source.path} to ${destination.path}")
                        }
                    }
            }
        }
}
