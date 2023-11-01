@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package fluxo.conf.impl

import org.gradle.api.Task
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
@Suppress("UNCHECKED_CAST")
internal inline fun <reified T : Task> TaskCollection<out Task>.named(
    name: String,
): TaskProvider<out T> = (this as TaskCollection<T>).named(name, T::class.java)