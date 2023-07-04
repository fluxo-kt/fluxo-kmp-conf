package fluxo.conf.impl

import org.gradle.api.Project
import org.gradle.api.provider.Provider


internal val EMPTY_FUN: (Any?) -> Unit = {}

@Suppress("MagicNumber")
internal val KOTLIN_1_8 = KotlinVersion(1, 8)

@Suppress("MagicNumber")
internal val KOTLIN_1_8_20 = KotlinVersion(1, 8, 20)

@Suppress("MagicNumber")
internal val KOTLIN_1_9 = KotlinVersion(1, 9)


/** @see org.jetbrains.kotlin.cli.common.toBooleanLenient */
internal fun String?.tryAsBoolean(): Boolean {
    return TRUE_VALUES.any { it.equals(this, ignoreCase = true) }
}

private val TRUE_VALUES = arrayOf("true", "1", "on", "y", "yes")


@Suppress("unused")
private fun Project.booleanProperty(name: String): Provider<Boolean> {
    val provider = stringProperty(name)
    return provider {
        provider.orNull.tryAsBoolean()
    }
}

private fun Project.stringProperty(name: String): Provider<String> {
    return provider {
        // Exclude extensions, look only for regular props
        val filter: (Any) -> Boolean = { it is CharSequence || it is Number }
        var value = findProperty(name)?.takeIf(filter)?.toString()
        if (value.isNullOrEmpty() && '.' !in name) {
            value = findProperty("org.gradle.project.$name")?.takeIf(filter)?.toString()
        }
        value
    }
}

private fun Project.envVar(name: String): Provider<String?> {
    return provider {
        providers.environmentVariable(name).orNull ?: System.getProperty(name)
    }
}


internal fun Project.envOrPropFlag(name: String): Provider<Boolean> {
    return provider {
        envVar(name).orNull != null || stringProperty(name).orNull.tryAsBoolean()
    }.memoize()
}

internal fun Project.envOrProp(name: String): Provider<String?> {
    return provider { envOrPropRaw(name) }.memoize()
}

internal fun Project.envOrPropRaw(name: String): String? {
    return envVar(name).orNull?.takeIf { it.isNotEmpty() }
        ?: stringProperty(name).orNull?.takeIf { it.isNotEmpty() }
}

internal fun Project?.envOrPropValueLenient(name: String): String? {
    // Check environment variable even if no project provided
    return if (this != null) envOrPropRaw(name) else System.getProperty(name)
}
