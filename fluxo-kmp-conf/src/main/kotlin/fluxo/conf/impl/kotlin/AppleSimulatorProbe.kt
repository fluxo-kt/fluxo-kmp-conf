package fluxo.conf.impl.kotlin

/**
 * Pure logic for the Apple-simulator availability gate, split out of `FkcSetupMultiplatform.kt` so
 * it is unit-testable: that file references `org.jetbrains.kotlin.gradle.*` (`compileOnly`), whose
 * file-class `<clinit>` would `NoClassDefFoundError` under test. Keeping this surface KGP-free
 * (zero such imports) keeps it testable without a Gradle project or a real `xcrun`.
 */

/** Task-name shapes that execute on an Apple simulator for [targetName] (id = capitalized name). */
internal fun isSimulatorTestTask(taskName: String, targetName: String, targetId: String): Boolean =
    taskName == "${targetName}Test" ||
        taskName == "${targetName}BackgroundTest" ||
        taskName == "compileTestKotlin$targetId" ||
        (taskName.startsWith("link") && taskName.endsWith("Test$targetId"))

/**
 * The `simctl` runtime platform a KMP [targetName] needs, or `null` when it runs on the host
 * (Linux/macOS/mingw) or a physical device and so needs no simulator probe. `iosX64`/`tvosX64`/
 * `watchosX64` are the legacy x86_64 *simulator* targets, hence grouped with the `*Simulator*`
 * ones; `iosArm64` & friends are device targets and map to `null`.
 */
internal fun appleSimulatorPlatform(targetName: String): String? = when {
    targetName.startsWith("iosSimulator") || targetName == "iosX64" -> "iOS"
    targetName.startsWith("tvosSimulator") || targetName == "tvosX64" -> "tvOS"
    targetName.startsWith("watchosSimulator") || targetName == "watchosX64" -> "watchOS"
    else -> null
}

/**
 * True when `xcrun simctl list runtimes available --json` [output] reports a runtime for the
 * [platform].
 *
 * Whitespace-insensitive on purpose: Apple pretty-prints (`"platform" : "iOS"`), but the spacing is
 * not contractual — a raw substring match on it silently breaks (simulator tests skipped, a false
 * green) the day the format compacts. Normalizing whitespace before matching the `"platform":"<p>"`
 * field tolerates both pretty and compact JSON. The closing quote anchors the match so `iOS` cannot
 * partial-hit `iOS-17-0`; the `available` subcommand already filters to installed+usable runtimes.
 */
internal fun simctlReportsRuntime(output: String, platform: String): Boolean =
    output.filterNot(Char::isWhitespace).contains("\"platform\":\"$platform\"")
