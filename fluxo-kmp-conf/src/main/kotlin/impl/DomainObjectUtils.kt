@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package impl

import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.PolymorphicDomainObjectContainer

internal inline fun <reified S : Any> DomainObjectCollection<in S>.withType(
    noinline configuration: S.() -> Unit,
) = withType(S::class.java, actionOf(configuration))

internal inline fun <reified S : Any> DomainObjectCollection<in S>.withType() =
    withType(S::class.java)


internal operator fun <T : Any> NamedDomainObjectCollection<T>.get(name: String): T =
    getByName(name)


internal fun <T> NamedDomainObjectContainer<T>.getOrCreate(
    name: String,
    invokeWhenCreated: (T.() -> Unit)? = null,
    configure: (T.() -> Unit)? = null,
): T {
    return if (name in names) {
        named(name).also { provider ->
            if (configure != null) provider.configure(actionOf(configure))
        }.get()
    } else {
        (if (configure != null) create(name, actionOf(configure)) else create(name)).also { value ->
            if (invokeWhenCreated != null) invokeWhenCreated(value)
        }
    }
}

internal fun <T> NamedDomainObjectContainer<T>.maybeRegister(
    name: String,
    configure: (T.() -> Unit)? = null,
): NamedDomainObjectProvider<T> {
    val entity = if (name in names) {
        named(name)
    } else {
        register(name)
    }
    if (configure != null) entity.configure(actionOf(configure))
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