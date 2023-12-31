package fluxo.gradle

import java.io.File
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider

internal fun <T : Any?> MutableCollection<String>.cliArg(
    name: String,
    value: T?,
    fn: (T) -> String = defaultToString(),
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
    fn: (T) -> String = defaultToString(),
) {
    cliArg(name, value.orNull, fn)
}

internal fun MutableCollection<String>.javaOption(value: String) {
    cliArg("--java-options", "'$value'")
}

private fun <T : Any?> defaultToString(): (T) -> String =
    {
        val asString = when (it) {
            is FileSystemLocation -> it.asFile.normalizedPath()
            is File -> it.normalizedPath()
            else -> it.toString()
        }
        "\"$asString\""
    }
