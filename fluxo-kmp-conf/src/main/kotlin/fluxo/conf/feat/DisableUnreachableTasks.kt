package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.d
import fluxo.conf.impl.l
import fluxo.conf.impl.v
import java.lang.System.currentTimeMillis
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
        val start = currentTimeMillis()
        if (!isProjectInSyncRun) {
            val allTasks = allTasks
            if (SHOW_DEBUG_LOGS || DEBUG_LOGS) {
                logger.v("ensureUnreachableTasksDisabled")
            }

            DisableUnreachableTasks(graph = this, allTasks = allTasks, logger = logger)
                .apply()

            logger.d(
                "ensureUnreachableTasksDisabled took ${currentTimeMillis() - start} ms" +
                    " (${allTasks.size} tasks total)",
            )
        }
    }
}

private class DisableUnreachableTasks(
    private val graph: TaskExecutionGraph,
    private val logger: Logger,
    private val allTasks: List<Task>,
) {
    private val rootTasks = findRootTasks()
    private val results = HashMap<Pair<Task, Task>, Boolean>()

    private fun findRootTasks(): List<Task> {
        val rootTasks = ArrayList<Task>()

        val children = HashSet<Task>(allTasks.size)
        allTasks.forEach {
            children += graph.getDependencies(it)
        }

        allTasks.forEach {
            if (it !in children) {
                rootTasks += it
            }
        }

        return rootTasks
    }

    fun apply() {
        for (it in allTasks) {
            // Disable inaccessible dependencies of enabled tasks
            if (it.enabled) {
                disableInaccessibleChildren(it)
            }
        }
    }

    private fun disableInaccessibleChildren(task: Task) {
        graph.getDependencies(task).forEach { child ->
            if (child.enabled) {
                if (!isTaskAccessible(task = child)) {
                    child.enabled = false
                    logger.l("Inaccessible task disabled: ${child.path}")
                    disableInaccessibleChildren(task = child)
                } else if (DEBUG_LOGS) {
                    logger.v("Task accessible: ${child.path}")
                }
            } else {
                logger.d("Task already disabled: ${child.path}")
                disableInaccessibleChildren(task = child)
            }
        }
    }

    private fun isTaskAccessible(task: Task): Boolean = rootTasks.any {
        val isPathExists = (it != task) && isPathExists(source = it, destination = task)

        if (DEBUG_LOGS && isPathExists) {
            logger.v("Task ${task.path} accessible from ${it.path}")
        }

        isPathExists
    }

    private fun isPathExists(source: Task, destination: Task): Boolean =
        results.getOrPut(source to destination) {
            when {
                !source.enabled -> false
                source == destination -> true.also {
                    if (DEBUG_LOGS) {
                        logger.d("Task reached: ${destination.path}")
                    }
                }

                else -> graph.getDependencies(source)
                    .any { isPathExists(source = it, destination = destination) }
                    .also {
                        if (DEBUG_LOGS && it) {
                            logger.v("Task path found from ${source.path} to ${destination.path}")
                        }
                    }
            }
        }
}

private const val DEBUG_LOGS = false
