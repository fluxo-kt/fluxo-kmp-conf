@file:Suppress("UNUSED_PARAMETER")

package fluxo.conf.impl

import kotlin.reflect.KClass
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.reflect.TypeOf

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
 */
internal inline fun <reified T : Any> ExtensionAware.the(type: KClass<T>? = null): T =
    extensions.getByType<T>()


internal inline fun <reified T : Any> ExtensionAware.configureExtensionIfAvailable(
    noinline action: T.() -> Unit,
): Boolean {
    return try {
        extensions.findByType(T::class.java)?.run(action)
        true
    } catch (_: NoClassDefFoundError) {
        false
    }
}

internal inline fun <reified T : Any> ExtensionAware.configureExtension(
    type: KClass<T>? = null,
    noinline action: T.() -> Unit,
) = extensions.configure<T>(type, action)

internal fun <T : Any> ExtensionAware.configureExtension(
    name: String,
    type: KClass<T>? = null,
    action: T.() -> Unit,
) = extensions.configure(name, action)


internal val ExtensionAware.extra: ExtraPropertiesExtension
    get() = extensions.extraProperties


/** Saves deep generic type requirements. */
internal inline fun <reified T> typeOf(): TypeOf<T> = object : TypeOf<T>() {}


internal inline fun <reified T : Any> ExtensionContainer.getByTypeStrict(): T =
    getByType(typeOf<T>())

internal inline fun <reified T : Any> ExtensionContainer.getByType(type: KClass<T>? = null): T =
    getByType(T::class.java)


internal inline fun <reified T : Any> ExtensionContainer.configure(
    type: KClass<T>? = null,
    noinline action: T.() -> Unit,
) {
    configure(T::class.java, actionOf(action))
}

internal fun <T : Any> ExtensionContainer.conf(
    name: String,
    type: KClass<T>? = null,
    action: T.() -> Unit,
) {
    configure(name, action)
}
