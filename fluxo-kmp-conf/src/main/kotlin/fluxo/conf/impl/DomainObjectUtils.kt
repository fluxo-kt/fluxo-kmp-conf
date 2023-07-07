@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package fluxo.conf.impl

import org.gradle.api.DomainObjectCollection
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
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
    val entity = if (name in names) {
        named(name)
    } else {
        register(name)
    }
    if (configure != null) entity.configure(configure)
    return entity
}


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
 * Creates a new [NamedDomainObjectContainer] for managing **named** objects of the specified type [T].
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
