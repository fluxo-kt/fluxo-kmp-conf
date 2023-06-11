package impl

import org.gradle.api.Project
import org.gradle.api.provider.Provider

private fun String?.tryAsBoolean(): Boolean {
    return arrayOf("true", "1", "on").any { it.equals(this, ignoreCase = true) }
}

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
    val env = providers.environmentVariable(name)
    return provider {
        env.orNull ?: System.getProperty(name)
    }
}


internal fun Project.envOrPropFlag(name: String): Provider<Boolean> {
    val env = envVar(name)
    val prop = stringProperty(name)
    return provider { env.orNull != null || prop.orNull.tryAsBoolean() }
}

internal fun Project.envOrProp(name: String): Provider<String?> {
    val env = envVar(name)
    val prop = stringProperty(name)
    return provider { env.orNull?.takeIf { it.isNotEmpty() } ?: prop.orNull }
}
