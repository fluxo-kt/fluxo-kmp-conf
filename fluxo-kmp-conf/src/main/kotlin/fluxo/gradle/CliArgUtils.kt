package fluxo.gradle

import fluxo.conf.impl.isWindowsOs
import java.io.File
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider

internal fun <T : Any?> MutableCollection<String>.cliArg(
    name: String,
    value: T?,
    base: File? = null,
    fn: (T) -> String = defaultToString(base = base),
) {
    if (value is Boolean) {
        if (value) add(name)
    } else if (value != null) {
        add(name)
        add(cliEscaped(fn(value)))
    }
}

internal fun <T : Any?> MutableCollection<String>.cliArg(
    name: String,
    value: Provider<T>,
    base: File? = null,
    fn: (T) -> String = defaultToString(base = base),
) {
    cliArg(name = name, value = value.orNull, base = base, fn = fn)
}

internal fun MutableCollection<String>.javaOption(value: String) =
    cliArg(name = "--java-options", value = value)

private fun <T : Any?> defaultToString(base: File? = null): (T) -> String =
    {
        val asString = when (it) {
            is FileSystemLocation -> it.asFile.normalizedPath(base)
            is File -> it.normalizedPath(base)
            else -> it.toString()
        }
        cliEscaped(asString)
    }

/**
 * Return CLI argument string for the given [value].
 * If the value contains spaces or special characters, returns quoted and escaped.
 * On Windows, it uses double quotes, single quotes on other platforms.
 */
private fun cliEscaped(value: String): String {
    if (!value.requiresQuotes()) {
        return value
    }
    val q = if (isWindowsOs) '"' else '\''
    return "$q${value.replace("$q", "\\$q")}$q"
}

private fun String.requiresQuotes(): Boolean {
    return isEmpty() || any {
        it.isWhitespace() || when (it) {
            '*', '?', '[', ']', '(', ')', '$', '|', ';', '&', '"', '\'' -> true
            else -> false
        }
    }
}
