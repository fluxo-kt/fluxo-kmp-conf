package fluxo.gradle

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
        add(fn(value))
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

internal fun MutableCollection<String>.javaOption(value: String) {
    cliArg(name = "--java-options", value = "'$value'")
}

private fun <T : Any?> defaultToString(base: File? = null): (T) -> String =
    {
        val asString = when (it) {
            is FileSystemLocation -> it.asFile.normalizedPath(base)
            is File -> it.normalizedPath(base)
            else -> it.toString()
        }
        "\"$asString\""
    }
