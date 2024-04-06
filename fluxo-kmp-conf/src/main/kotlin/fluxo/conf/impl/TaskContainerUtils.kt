@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package fluxo.conf.impl

import org.gradle.api.Task
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

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
    if (HAS_NEW_NAMED_METHOD) {
        // Since Gradle 8.6, a new method `named(Spec<String>)` is available.
        // It provides lazy name-based filtering of tasks
        // without triggering the creation of the tasks,
        // even when the task was not part of the build execution.
        // https://docs.gradle.org/8.6/release-notes.html#lazy-name-based-filtering-of-tasks
        // https://docs.gradle.org/8.6/javadoc/org/gradle/api/NamedDomainObjectSet.html#named-org.gradle.api.specs.Spec-
        try {
            return uncheckedCast(named(nameFilter))
        } catch (_: NoSuchMethodError) {
        }
    }

    // Fallback for older Gradle versions
    return uncheckedCast(matching { nameFilter.isSatisfiedBy(it.name) })
}

private val HAS_NEW_NAMED_METHOD = GradleVersion.current() >= GradleVersion.version("8.6")
