package fluxo.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

internal fun Project.addToCheckAndTestDependencies(
    path: Any,
    checkOnly: Boolean = false,
) {
    val tasks = tasks
    val bind = Action<Task> { dependsOn(path) }
    tasks.named(CHECK_TASK_NAME, bind)
    if (!checkOnly) {
        tasks.named(TEST_TASK_NAME, bind)
    }
}
