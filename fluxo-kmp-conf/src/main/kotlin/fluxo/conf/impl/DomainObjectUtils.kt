@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER", "TooManyFunctions")

package fluxo.conf.impl

import org.gradle.api.DomainObjectCollection
import org.gradle.api.DomainObjectSet
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.model.ObjectFactory

internal inline fun <reified S : Any> DomainObjectCollection<in S>.withType(
    noinline configuration: S.() -> Unit,
) = withType(S::class.java, actionOf(configuration))

internal inline fun <reified S : Any> DomainObjectCollection<in S>.withType() =
    withType(S::class.java)


internal operator fun <T : Any> NamedDomainObjectCollection<T>.get(name: String): T =
    getByName(name)


internal fun <T : Any> NamedDomainObjectContainer<T>.getOrCreate(
    name: String,
    onCreated: (T.() -> Unit)? = null,
): T {
    findByName(name)?.let { return it }
    return create(name).also {
        onCreated?.invoke(it)
    }
}

internal fun <T : Any> NamedDomainObjectContainer<T>.maybeRegister(
    name: String,
    configure: (T.() -> Unit)? = null,
): NamedDomainObjectProvider<T> {
    val entity = when {
        has(name) -> named(name)
        else -> register(name)
    }
    if (configure != null) entity.configure(configure)
    return entity
}


internal fun <T : Named> NamedDomainObjectSet<T>.namedLazy(name: String): NamedDomainObjectSet<T> {
    return try {
        // Since Gradle 8.6, a new method `named(Spec<String>)` is available.
        // It provides lazy name-based filtering of tasks
        // without triggering the creation of the tasks,
        // even when the task was not part of the build execution.
        // https://docs.gradle.org/8.6/release-notes.html#lazy-name-based-filtering-of-tasks
        // https://docs.gradle.org/8.6/javadoc/org/gradle/api/NamedDomainObjectSet.html#named-org.gradle.api.specs.Spec-
        named { it == name }
    } catch (_: NoSuchMethodError) {
        // Eager fallback for older Gradle versions
        matching { it.name == name }
    }
}

internal fun <T : Any> NamedDomainObjectCollection<T>.namedOrNull(
    name: String,
): NamedDomainObjectProvider<T>? {
    return if (has(name)) named(name) else null
}

internal fun NamedDomainObjectCollection<*>.has(name: String): Boolean = name in names


internal inline fun <reified T : Any> PolymorphicDomainObjectContainer<in T>.register(
    name: String,
): NamedDomainObjectProvider<T> = register(name, T::class.java)

internal inline fun <reified T : Any> PolymorphicDomainObjectContainer<in T>.register(
    name: String,
    noinline configuration: T.() -> Unit,
): NamedDomainObjectProvider<T> = register(name, T::class.java, actionOf(configuration))

internal inline fun <reified U : Any> PolymorphicDomainObjectContainer<in U>.create(
    name: String,
    noinline configuration: U.() -> Unit,
) = create(name, U::class.java, actionOf(configuration))

internal inline fun <reified U : Any> PolymorphicDomainObjectContainer<in U>.create(name: String) =
    create(name, U::class.java)

internal inline fun <reified U : Any> PolymorphicDomainObjectContainer<in U>.maybeCreate(
    name: String,
) = maybeCreate(name, U::class.java)


/**
 * Creates a new [NamedDomainObjectContainer]
 * for managing **named** objects of the specified type [T].
 *
 * @see org.gradle.api.model.ObjectFactory.domainObjectContainer
 */
internal inline fun <reified T : Any> ObjectFactory.container(): NamedDomainObjectContainer<T> =
    domainObjectContainer(T::class.java)

/**
 * Creates a new [DomainObjectSet] for managing objects of the specified type [T].
 *
 * @see org.gradle.api.model.ObjectFactory.domainObjectSet
 */
internal inline fun <reified T : Any> ObjectFactory.set(): DomainObjectSet<T> =
    domainObjectSet(T::class.java)
