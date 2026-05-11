package fluxo.conf.feat

import MAIN_SOURCE_SET_POSTFIX
import fluxo.conf.impl.splitCamelCase

private const val DETEKT_TASK_NAME = "detekt"

internal fun getTaskDetailsFromName(
    name: String,
    allowNonDetekt: Boolean = false,
): DetectedTaskDetails {
    val parts = name.splitCamelCase()
    var list = if (!allowNonDetekt) {
        require(parts.isNotEmpty() && parts[0] == DETEKT_TASK_NAME) {
            "Unexpected detect task name: $name"
        }
        parts.drop(1)
    } else {
        parts
    }

    val last = parts.lastOrNull()
    val isTest = last.equals("Test", ignoreCase = true)
    val isMain = !isTest && last.equals(MAIN_SOURCE_SET_POSTFIX, ignoreCase = true)
    if (isTest || isMain) {
        list = list.dropLast(1)
    }

    val isMetadata = list.firstOrNull().equals("Metadata", ignoreCase = true)
    if (isMetadata) {
        list = list.drop(1)
    }

    return DetectedTaskDetails(
        platform = detectPlatformFromParts(list),
        isMain = isMain,
        isTest = isTest,
        isMetadata = isMetadata,
        taskName = name,
    )
}

private fun detectPlatformFromParts(parts: List<String>): DetectedTaskPlatform? {
    val first = parts.firstOrNull()
    val second = parts.getOrNull(1)
    return when {
        first.equals("Common", ignoreCase = true) ->
            detectPlatformFromString(second)

        first.equals("Non", ignoreCase = true) && second.equals("Jvm", ignoreCase = true) ->
            DetectedTaskPlatform.NON_JVM

        else -> detectPlatformFromString(first)
    }
}

@Suppress("CyclomaticComplexMethod")
private fun detectPlatformFromString(platform: String?): DetectedTaskPlatform? = when {
    platform.isNullOrEmpty() ||
        platform.equals("Common", ignoreCase = true)
    -> null

    platform.equals("Native", ignoreCase = true) -> DetectedTaskPlatform.NATIVE
    platform.equals("NonJvm", ignoreCase = true) -> DetectedTaskPlatform.NON_JVM
    platform.equals("Unix", ignoreCase = true) ||
        platform.equals("Nix", ignoreCase = true)
    -> DetectedTaskPlatform.UNIX
    platform.equals("Web", ignoreCase = true) -> DetectedTaskPlatform.WEB
    platform.equals("Js", ignoreCase = true) -> DetectedTaskPlatform.JS
    platform.equals("Wasm", ignoreCase = true) -> DetectedTaskPlatform.WASM
    platform.equals("Linux", ignoreCase = true) -> DetectedTaskPlatform.LINUX

    platform.equals("Android", ignoreCase = true) ||
        platform.equals("bundle", ignoreCase = true) ||
        platform.equals("assemble", ignoreCase = true)
    -> DetectedTaskPlatform.ANDROID

    platform.equals("Mingw", ignoreCase = true) ||
        platform.equals("Win", ignoreCase = true) ||
        platform.equals("Windows", ignoreCase = true)
    -> DetectedTaskPlatform.MINGW

    platform.equals("Jvm", ignoreCase = true) ||
        platform.equals("Jmh", ignoreCase = true) ||
        platform.equals("Dokka", ignoreCase = true) ||
        platform.equals("Java", ignoreCase = true) ||
        platform.equals("Experimental", ignoreCase = true)
    -> DetectedTaskPlatform.JVM

    platform.equals("Darwin", ignoreCase = true) ||
        platform.equals("Apple", ignoreCase = true) ||
        platform.equals("Ios", ignoreCase = true) ||
        platform.equals("Watchos", ignoreCase = true) ||
        platform.equals("Tvos", ignoreCase = true) ||
        platform.equals("Macos", ignoreCase = true)
    -> DetectedTaskPlatform.APPLE

    else -> DetectedTaskPlatform.UNKNOWN
}

internal data class DetectedTaskDetails(
    val platform: DetectedTaskPlatform?,
    val isMain: Boolean,
    val isTest: Boolean,
    val isMetadata: Boolean,
    val taskName: String,
)

/**
 * @see org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
 * @see org.jetbrains.kotlin.konan.target.Family
 */
internal enum class DetectedTaskPlatform {
    APPLE,
    LINUX,
    MINGW,
    JS,
    WASM,
    ANDROID,
    JVM,
    NATIVE,
    UNIX,
    WEB,
    NON_JVM,
    UNKNOWN,
}
