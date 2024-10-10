package fluxo.conf.impl

import org.gradle.api.Project
import org.gradle.api.provider.Provider


internal val EMPTY_FUN: (Any?) -> Unit = {}


/** @see org.jetbrains.kotlin.cli.common.toBooleanLenient */
internal fun String?.tryAsBoolean(): Boolean {
    return TRUE_VALUES.any { it.equals(this, ignoreCase = true) }
}

private val TRUE_VALUES = arrayOf("true", "1", "on", "y", "yes")


private fun Project.stringPropValue(name: String): String? {
    // Exclude extensions, look only for regular props
    val filter: (Any) -> Boolean = { it is CharSequence || it is Number }
    var value = findProperty(name)?.takeIf(filter)?.toString()
    if (value.isNullOrEmpty() && '.' !in name) {
        value = findProperty("org.gradle.project.$name")?.takeIf(filter)?.toString()
    }
    return value
}

private fun Project.envVarValue(name: String): String? {
    return providers.environmentVariable(name).orNull ?: System.getProperty(name)
}


internal fun Project.envOrPropFlag(name: String): Provider<Boolean> =
    provider { envOrPropFlagValue(name) }.memoizeSafe(logger)

internal fun Project.envOrPropFlagValue(name: String): Boolean {
    return envVarValue(name) != null || stringPropValue(name).tryAsBoolean()
}


internal fun Project.envOrProp(name: String): Provider<String?> {
    return provider { envOrPropValue(name) }.memoizeSafe(logger)
}

internal fun Project.envOrPropValue(name: String): String? {
    return envVarValue(name)?.takeIf { it.isNotEmpty() }
        ?: stringPropValue(name)?.takeIf { it.isNotEmpty() }
}

internal fun Project?.envOrPropValueLenient(name: String): String? {
    // Check environment variable even if no project provided
    return if (this != null) envOrPropValue(name) else System.getProperty(name)
}


/**
 * Adds all elements of the given [elements] array to this [MutableCollection].
 */
internal fun <T> MutableCollection<in T>.addAll(vararg elements: T) = addAll(elements)
