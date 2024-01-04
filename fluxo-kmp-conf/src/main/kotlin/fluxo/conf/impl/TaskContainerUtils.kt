@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package fluxo.conf.impl

import org.gradle.api.Task
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/** @see org.gradle.api.tasks.TaskContainer.register */
internal inline fun <reified T : Task> TaskContainer.register(name: String): TaskProvider<T> =
    register(name, T::class.java)

/** @see org.gradle.api.tasks.TaskContainer.register */
internal inline fun <reified T : Task> TaskContainer.register(
    name: String,
    noinline configuration: T.() -> Unit,
): TaskProvider<T> = register(name, T::class.java, actionOf(configuration))

/** @see org.gradle.api.tasks.TaskContainer.register */
internal inline fun <reified T : Task> TaskContainer.register(
    name: String,
    vararg arguments: Any,
): TaskProvider<T> = register(name, T::class.java, *arguments)


/** @see org.gradle.api.tasks.TaskCollection.named */
internal inline fun <reified T : Task> TaskCollection<out Task>.named(
    name: String,
): TaskProvider<out T> = uncheckedCast<TaskCollection<T>>(this).named(name, T::class.java)


/**
 * Compatibility method for Gradle 8.6+ and older.
 */
internal fun <T : Task, R : T> TaskCollection<T>.namedCompat(
    nameFilter: Spec<String>,
): TaskCollection<R> {
    // Since Gradle 8.6, there is a new method `named(Spec<String>)`.
    // It provides lazy name-based filtering of tasks without triggering the creation of the tasks,
    // even when the task was not part of the build execution.
    // https://docs.gradle.org/8.6-rc-1/javadoc/org/gradle/api/NamedDomainObjectSet.html#named-org.gradle.api.specs.Spec-
    // TODO: Add method usage when compile against Gradle 8.6+.

    return uncheckedCast(matching { nameFilter.isSatisfiedBy(it.name) })
}
