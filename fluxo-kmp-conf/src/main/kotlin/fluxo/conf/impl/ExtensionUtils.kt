@file:Suppress("UNUSED_PARAMETER", "TooManyFunctions")

package fluxo.conf.impl

import kotlin.reflect.KClass
import org.gradle.api.Action
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.reflect.TypeOf

internal fun ExtensionAware.hasExtension(name: String): Boolean =
    extensions.findByName(name) != null

internal inline fun <reified T : Any> ExtensionAware.hasExtension(): Boolean {
    return try {
        extensions.findByType(T::class.java) != null
    } catch (_: NoClassDefFoundError) {
        false
    }
}

internal inline fun ExtensionAware.hasExtension(clazz: () -> KClass<*>): Boolean {
    return try {
        extensions.findByType(clazz().java) != null
    } catch (_: NoClassDefFoundError) {
        false
    }
}


/**
 * Looks for the extension of a given type (useful to avoid casting).
 * Throws an exception if none found.
 *
 * @see org.gradle.api.plugins.ExtensionContainer.getByType
 * @throws UnknownDomainObjectException When the given extension isn't found.
 * @throws NoClassDefFoundError if [T] isn't on the classpath!
 */
internal inline fun <reified T : Any> ExtensionAware.the(type: KClass<T>? = null): T =
    extensions.getByType<T>()

/**
 *
 * @throws NoClassDefFoundError if [T] isn't on the classpath!
 */
internal inline fun <reified T : Any> ExtensionAware.configureExtensionIfAvailable(
    @Suppress("UNUSED_PARAMETER") name: String? = null,
    noinline action: T.() -> Unit,
): Boolean {
    return try {
        extensions.findByType(T::class.java)?.run(action)
        true
    } catch (_: NoClassDefFoundError) {
        false
    }
}

/**
 *
 * @throws NoClassDefFoundError if [T] isn't on the classpath!
 */
internal inline fun <reified T : Any> ExtensionAware.configureExtension(
    type: KClass<T>? = null,
    noinline action: T.() -> Unit,
) = extensions.configure(T::class.java, actionOf(action))

/**
 *
 * @throws NoClassDefFoundError if [T] isn't on the classpath!
 */
internal fun <T : Any> ExtensionAware.configureExtension(
    name: String,
    type: KClass<T>? = null,
    action: Action<in T>,
) = extensions.configure(name, action)


internal val ExtensionAware.extra: ExtraPropertiesExtension
    get() = extensions.extraProperties


/** Saves deep generic type requirements. */
internal inline fun <reified T> typeOf(): TypeOf<T> = object : TypeOf<T>() {}


internal inline fun <reified T : Any> ExtensionContainer.getByTypeStrict(): T =
    getByType(typeOf<T>())

internal inline fun <reified T : Any> ExtensionContainer.getByType(type: KClass<T>? = null): T =
    getByType(T::class.java)

internal inline fun <reified T : Any> ExtensionContainer.getByName(
    name: String,
    type: KClass<T>? = null,
): T = findByName(name) as T

internal inline fun <reified T : Any> ExtensionContainer.findByType(type: KClass<T>? = null): T? =
    findByType(T::class.java)

internal inline fun <reified T : Any> ExtensionContainer.find(
    name: String,
    type: KClass<T>? = null,
): T? = findByName(name) as? T
