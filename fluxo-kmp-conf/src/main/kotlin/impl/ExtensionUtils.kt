package impl

import kotlin.reflect.KClass
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.reflect.TypeOf

internal inline fun <reified T : Any> ExtensionAware.hasExtension(): Boolean {
    return try {
        extensions.findByType(typeOf<T>()) != null
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
internal inline fun <reified T : Any> ExtensionAware.the(): T = extensions.getByType<T>()


internal inline fun <reified T : Any> ExtensionAware.configureExtensionIfAvailable(
    noinline action: T.() -> Unit,
): Boolean {
    return try {
        extensions.findByType(typeOf<T>())?.run(action)
        true
    } catch (_: NoClassDefFoundError) {
        false
    }
}

internal inline fun <reified T : Any> ExtensionAware.configureExtension(
    noinline action: T.() -> Unit,
) = extensions.configure<T>(action)

internal fun <T : Any> ExtensionAware.configureExtension(name: String, action: T.() -> Unit) =
    extensions.configure(name, actionOf(action))


internal val ExtensionAware.extra: ExtraPropertiesExtension
    get() = extensions.extraProperties


internal inline fun <reified T> typeOf(): TypeOf<T> = object : TypeOf<T>() {}


internal inline fun <reified T : Any> ExtensionContainer.getByType(): T = getByType(typeOf<T>())

internal inline fun <reified T : Any> ExtensionContainer.configure(noinline action: T.() -> Unit) {
    configure(typeOf<T>(), actionOf(action))
}

internal fun <T : Any> ExtensionContainer.conf(name: String, action: T.() -> Unit) {
    configure(name, actionOf(action))
}
