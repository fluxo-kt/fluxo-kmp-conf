package fluxo.log

import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Problems

/**
 * Prints a warning the consumer must act on and reports it to Gradle's Problems API, so the
 * problems report and IDEs show it with its [fix].
 *
 * The console line stays: Gradle writes reported problems only into its HTML report. The API is
 * incubating, so where it is missing or changed (`LinkageError`) only the line remains; its shape
 * is the same on Gradle 9.0 and 9.8 (`gradle-problems-api` jars).
 */
internal fun Project.reportProblem(problem: FluxoProblem, message: String, fix: String? = null) {
    logger.w(if (fix == null) message else "$message $fix")
    try {
        val id = ProblemId.create(problem.id, problem.displayName, GROUP)
        objects.newInstance(ProblemsHolder::class.java).problems.reporter.report(id) {
            contextualLabel(message)
            if (fix != null) solution(fix)
            // No `severity(WARNING)`: `report` already means a warning, and Gradle 9.8
            // deprecates setting it.
        }
    } catch (_: LinkageError) {
    }
}

/** One entry per kind of warning: the problems report groups by it. */
internal enum class FluxoProblem(val displayName: String) {
    PLUGIN_NOT_ON_BUILD_CLASSPATH("Plugin missing from the build classpath"),
    TOOL_TOO_OLD("Tool version too old for fluxo-kmp-conf"),
    TOOL_UNSUPPORTED_ON_GRADLE("Tool can't run on this Gradle"),
    JDK_API_NOT_LIMITED("JDK API not limited to the JVM target"),
    KOTLIN_VERSIONS("Kotlin version settings adjusted"),
    TESTS_OFF("Tests are off"),
    SETUP_STEP_SKIPPED("Setup step skipped"),
    DEPRECATED_SETTING("Deprecated setting"),
    JS_TOOL_TOO_NEW("JS tool version newer than Kotlin supports"),
    ;

    val id: String = name.lowercase().replace('_', '-')
}

private val GROUP by lazy { ProblemGroup.create("fluxo-kmp-conf", "fluxo-kmp-conf") }

// `Problems` is only injectable.
internal open class ProblemsHolder @Inject constructor(val problems: Problems)
