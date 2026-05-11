@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package fluxo.conf.impl

import org.gradle.api.Task
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/** @see org.gradle.api.tasks.TaskContainer.register */
internal inline fun <reified T : Task> TaskContainer.registerCompat(name: String): TaskProvider<T> =
    register(name, T::class.java)

/** @see org.gradle.api.tasks.TaskContainer.register */
internal inline fun <reified T : Task> TaskContainer.registerCompat(
    name: String,
    noinline configuration: T.() -> Unit,
): TaskProvider<T> = register(name, T::class.java, actionOf(configuration))

/** @see org.gradle.api.tasks.TaskContainer.register */
internal inline fun <reified T : Task> TaskContainer.registerCompat(
    name: String,
    vararg arguments: Any,
): TaskProvider<T> = register(name, T::class.java, *arguments)


/** @see org.gradle.api.tasks.TaskCollection.named */
internal inline fun <reified T : Task> TaskCollection<out Task>.named(
    name: String,
): TaskProvider<out T> = uncheckedCast<TaskCollection<T>>(this).named(name, T::class.java)


/**
 * Lazy name-based filtering of tasks without triggering creation.
 *
 * @see org.gradle.api.NamedDomainObjectSet.named
 */
internal fun <T : Task, R : T> TaskCollection<T>.namedCompat(
    nameFilter: Spec<String>,
): TaskCollection<R> = uncheckedCast(named(nameFilter))
