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

/**
 * WARN: Prefer using [hasExtension] with name parameter instead of this if possible.
 *
 * Can't use reified type parameter because of [NoClassDefFoundError]
 * if the type isn't on the classpath!
 *
 * @see org.gradle.api.plugins.ExtensionContainer.findByType
 */
internal inline fun ExtensionAware.hasExtension(clazz: () -> KClass<*>): Boolean {
    return try {
        extensions.findByType(clazz().java) != null
    } catch (_: NoClassDefFoundError) {
        false
    }
}


/**
 * Looks for the extension of a given type (useful to avoid casting).
 *
 * Throws an exception if none found!
 *
 * @see org.gradle.api.plugins.ExtensionContainer.getByType
 * @throws UnknownDomainObjectException When the given extension isn't found.
 * @throws NoClassDefFoundError if [T] isn't on the classpath!
 */
internal inline fun <reified T : Any> ExtensionAware.the(type: KClass<T>? = null): T =
    extensions.getByType<T>()


/**
 * Safely looks for the extension of a given name and executes the given action on it.
 *
 * Can't use reified type parameter because of [NoClassDefFoundError]
 * if the type isn't on the classpath!
 *
 * @see org.gradle.api.plugins.ExtensionContainer.findByName
 */
internal inline fun <T : Any> ExtensionAware.configureExtensionIfAvailable(
    name: String,
    action: T.() -> Unit,
): Boolean {
    try {
        val ext = extensions.findByName(name)
        if (ext != null) {
            @Suppress("UNCHECKED_CAST")
            action(ext as T)
            return true
        }
    } catch (_: NoClassDefFoundError) {
    }
    return false
}

/**
 * Safely looks for the extension of a given type and executes the given action on it.
 *
 * WARN:
 * Prefer using [configureExtensionIfAvailable] with name parameter instead of this if possible.
 *
 * Can't use reified type parameter because of [NoClassDefFoundError]
 * if the type isn't on the classpath!
 *
 * @see org.gradle.api.plugins.ExtensionContainer.findByType
 */
internal inline fun <T : Any> ExtensionAware.configureExtensionIfAvailable(
    clazz: () -> KClass<T>,
    action: T.() -> Unit,
): Boolean {
    try {
        val ext = extensions.findByType(clazz().java)
        if (ext != null) {
            action(ext)
            return true
        }
    } catch (_: NoClassDefFoundError) {
    }
    return false
}


/**
 *
 * @throws NoClassDefFoundError if [T] isn't on the classpath!
 */
internal fun <T : Any> ExtensionAware.configureExtension(
    name: String,
    type: KClass<T>? = null,
    action: Action<in T>,
) = extensions.configure(name, action)

/**
 * WARN: Prefer using [configureExtension] with name parameter instead of this if possible.
 *
 * NOTE: Uses [actionOf] to avoid bloating the bytecode.
 *
 * @throws NoClassDefFoundError if [T] isn't on the classpath!
 */
internal inline fun <reified T : Any> ExtensionAware.configureExtension(
    type: KClass<T>? = null,
    noinline action: T.() -> Unit,
) = extensions.configure(T::class.java, actionOf(action))



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

/**
 *
 * @throws NoClassDefFoundError if [T] isn't on the classpath!
 */
internal inline fun <T : Any> ExtensionContainer.findByType(clazz: () -> KClass<*>): T? {
    return try {
        @Suppress("UNCHECKED_CAST")
        findByType(clazz().java) as T
    } catch (_: NoClassDefFoundError) {
        null
    }
}

/**
 *
 * @throws NoClassDefFoundError if [T] isn't on the classpath!
 */
internal inline fun <reified T : Any> ExtensionContainer.find(
    name: String,
    type: KClass<T>? = null,
): T? = findByName(name) as T?
