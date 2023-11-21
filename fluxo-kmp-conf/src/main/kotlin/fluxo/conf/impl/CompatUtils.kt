@file:Suppress("DEPRECATION", "KotlinRedundantDiagnosticSuppress")

package fluxo.conf.impl

import java.util.Locale
import java.util.Optional

internal fun <T> Optional<T>.getOrNull(): T? = if (isPresent) get() else null


/** @see org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty */
internal inline fun <T, C : Collection<T>, O> C.ifNotEmpty(body: C.() -> O?): O? =
    if (isNotEmpty()) body() else null


internal fun String.lowercase(): String = toLowerCase(Locale.US)

internal fun Char.uppercaseChar(): Char = toUpperCase()

/**
 *
 * @see org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
 */
internal fun String.capitalizeAsciiOnly(): String {
    if (isEmpty()) return this
    val c = this[0]
    return if (c in 'a'..'z') c.uppercaseChar() + substring(1) else this
}
