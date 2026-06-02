package fluxo.conf.feat

/**
 * Pure BCV API-target mapping, split out of `SetupBinaryCompatibilityValidator.kt` so it can be
 * unit-tested. That file references `kotlinx.validation.*` / `org.jetbrains.kotlin.gradle.*`
 * (`compileOnly`); its file-class `<clinit>` would resolve those at test time and throw
 * `NoClassDefFoundError`. Keeping this surface KGP-free (zero such imports) keeps it testable.
 */
internal enum class ApiTarget {
    ANDROID,
    JVM,
    JS,
}

/**
 * Maps a BCV `<target>ApiCheck` task name to the [ApiTarget] the per-target enable/disable filter
 * governs, or `null` for any name outside its concern.
 *
 * Open-world by contract: BCV creates compare tasks beyond android/jvm/js — the `apiCheck`
 * umbrella and, once KLib validation is on, `klibApiCheck` — none of which this filter toggles.
 * Returns `null` (skip, leave the task untouched) for those; it MUST NOT throw. A throw here runs
 * inside `tasks.withType<KotlinApiCompareTask> { … }` configuration and crashes the build for every
 * KMP consumer enabling apiValidation without both JVM and ANDROID present (the prior `else ->
 * error(…)` did exactly this — regression seed in [getTargetForTaskName] tests).
 */
internal fun getTargetForTaskName(taskName: String): ApiTarget? =
    when (taskName.removeSuffix("ApiCheck").takeIf { it != taskName }) {
        "android" -> ApiTarget.ANDROID
        "jvm" -> ApiTarget.JVM
        "js" -> ApiTarget.JS
        else -> null
    }
