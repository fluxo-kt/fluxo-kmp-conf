package fluxo.conf.impl

internal fun String.uppercaseFirstChar(): String =
    transformFirstCharIfNeeded(
        shouldTransform = Char::isLowerCase,
        transform = Char::uppercaseChar,
    )

internal fun String.lowercaseFirstChar(): String =
    transformFirstCharIfNeeded(
        shouldTransform = Char::isUpperCase,
        transform = Char::lowercaseChar,
    )

private inline fun String.transformFirstCharIfNeeded(
    shouldTransform: (Char) -> Boolean,
    transform: (Char) -> Char,
): String {
    if (isNotEmpty()) {
        val firstChar = this[0]
        if (shouldTransform(firstChar)) {
            val sb = java.lang.StringBuilder(length)
            sb.append(transform(firstChar))
            sb.append(this, 1, length)
            return sb.toString()
        }
    }
    return this
}

internal fun joinDashLowercaseNonEmpty(vararg parts: String): String =
    parts.filter { it.isNotEmpty() }
        .joinToString(separator = "-") { it.lc() }

internal fun joinLowerCamelCase(vararg parts: String): String =
    parts.withIndex().joinToString(separator = "") { (i, part) ->
        if (i == 0) part.lowercaseFirstChar() else part.uppercaseFirstChar()
    }

internal fun joinUpperCamelCase(vararg parts: String): String =
    parts.joinToString(separator = "") { it.uppercaseFirstChar() }
