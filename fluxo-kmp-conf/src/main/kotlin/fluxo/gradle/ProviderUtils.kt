package fluxo.gradle

import fluxo.conf.impl.uncheckedCast
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

internal inline fun <reified T> ObjectFactory.new(vararg params: Any): T =
    newInstance(T::class.java, *params)

internal inline fun <reified T : Any> ObjectFactory.nullableProperty(): Property<T?> =
    uncheckedCast(property(T::class.java))

internal inline fun <reified T : Any> ObjectFactory.notNullProperty(): Property<T> =
    property(T::class.java)

internal inline fun <reified T : Any> ObjectFactory.notNullProperty(defaultValue: T): Property<T> =
    property(T::class.java).value(defaultValue)

internal inline fun <reified T : Any> ObjectFactory.notNullProperty(
    defaultValue: Provider<T>,
): Property<T> = property(T::class.java).value(defaultValue)

internal inline fun <reified T> Provider<T>.toProperty(objects: ObjectFactory): Property<T> =
    objects.property(T::class.java).value(this)

internal fun Provider<String?>.toBooleanProvider(defaultValue: Boolean): Provider<Boolean> =
    orElse(defaultValue.toString()).map { "true" == it }
