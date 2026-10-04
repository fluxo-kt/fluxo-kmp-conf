package fluxo.shrink

/**
 * Returns [name], or `name-2`, `name-3`, … before the extension, whichever is not yet in
 * [taken], and records the result.
 *
 * Shrinker inputs are copied to outputs by file name, but distinct artifacts can share one:
 * Compose Multiplatform 1.12 ships `org.jetbrains.compose.runtime:runtime-saveable-desktop` next to
 * `androidx.compose.runtime:runtime-saveable-desktop`, both `runtime-saveable-desktop-1.12.1.jar`.
 * ProGuard then fails with "The same output jar … is specified twice".
 * [taken] holds lower-cased names because macOS and Windows file systems ignore case.
 * The input order decides which file keeps the plain name, so outputs stay stable between runs.
 */
internal fun uniqueFileName(name: String, taken: MutableSet<String>): String {
    if (taken.add(name.lowercase())) return name
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    return generateSequence(2) { it + 1 }
        .map { "$base-$it$ext" }
        .first { taken.add(it.lowercase()) }
}
