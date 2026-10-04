package fluxo.conf

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.flow.BuildWorkResult
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * Collects what the plugin prints when the build ends, for one build.
 *
 * [BuildEndReportAction] is registered once, and only when there is something to report, so a
 * normal build registers nothing. Its providers are read when the configuration-cache entry is
 * stored, after every module has configured, so additions made later still get printed.
 */
internal class BuildEndReport(
    private val flowScope: FlowScope,
    private val flowProviders: FlowProviders,
    private val rootProject: Project,
    private val explain: Boolean,
) {
    private val warnings = ConcurrentLinkedQueue<() -> String>()
    private val decisions = ConcurrentLinkedQueue<String>()
    private val failureHints = ConcurrentHashMap<String, String>()
    private val registered = AtomicBoolean()

    init {
        // The FLUXO_EXPLAIN block prints even when no module derived anything.
        if (explain) register()
    }

    /**
     * Prints [message] as a warning when the build ends, also on a configuration-cache hit.
     * Built when the report is read, so an aggregated message includes everything recorded later.
     */
    fun warn(message: () -> String) {
        register()
        warnings += message
    }

    /** Keeps [line] for the `FLUXO_EXPLAIN` block; a no-op without the flag. */
    fun explain(line: String) {
        if (explain) decisions += line
    }

    /**
     * Prints [hint] as a warning when the build fails in the task at [taskPath], and stays
     * silent otherwise: a hint explains one failure, so printing it on a passing build or an
     * unrelated failure would be noise.
     */
    fun hintOnFailure(taskPath: String, hint: String) {
        register()
        failureHints[taskPath] = hint
    }

    private fun register() {
        if (!registered.compareAndSet(false, true)) return
        // An explicit `Action`, not a trailing lambda: Detekt 1.23.8's IgnoredReturnValue
        // crashes the whole analysis (NPE in `findPackage()`) on the SAM-adapted call.
        flowScope.always(
            BuildEndReportAction::class.java,
            Action {
                parameters.warnings.set(rootProject.provider { warnings.map { it() } })
                parameters.explain.set(explain)
                parameters.decisions.set(rootProject.provider { decisions.toList() })
                parameters.failureHints.set(rootProject.provider { failureHints.toMap() })
                parameters.failure.set(flowProviders.buildWorkResult.map(FailureText()))
            },
        )
    }
}

/**
 * Prints what the plugin reports when the build ends: warnings due at the end of the build, and
 * the `FLUXO_EXPLAIN` block of derived settings.
 *
 * A flow action, not a build service or a listener: Gradle stores its parameters in the
 * configuration-cache entry and runs it on a cache hit too, while anything registered during
 * configuration (a lambda kept in a service, a listener) is never registered on a hit, so its
 * output silently disappears. Runs whether the build succeeds or fails.
 *
 * @see BuildEndReport
 */
internal abstract class BuildEndReportAction : FlowAction<BuildEndReportAction.Parameters> {
    interface Parameters : FlowParameters {
        @get:Input
        val warnings: ListProperty<String>

        @get:Input
        val explain: Property<Boolean>

        @get:Input
        val decisions: ListProperty<String>

        /** Task path → hint, see [BuildEndReport.hintOnFailure]. */
        @get:Input
        val failureHints: MapProperty<String, String>

        /** Messages of the build failure and its causes, empty when the build passed. */
        @get:Input
        val failure: Property<String>
    }

    override fun execute(parameters: Parameters) {
        val logger = Logging.getLogger(BuildEndReportAction::class.java)
        parameters.warnings.get().forEach(logger::warn)
        val failure = parameters.failure.get()
        for ((path, hint) in parameters.failureHints.get()) {
            // Gradle names the failed task as "Execution failed for task ':lib:compileKotlin'."
            if ("task '$path'" in failure) logger.warn(hint)
        }
        if (parameters.explain.get()) {
            val lines = parameters.decisions.get()
            val body = if (lines.isEmpty()) "  (none)" else lines.joinToString("\n") { "  $it" }
            logger.lifecycle("$EXPLAIN_HEADER\n$body")
        }
    }
}

/**
 * The messages of a build failure and its cause chain. Gradle wraps task failures in one
 * multi-cause exception whose `cause` is the first failure, so a later one under `--continue`
 * is not seen: reaching the others needs Gradle's internal `MultiCauseException`, and a hint
 * missed there costs less than an internal API that may break on a Gradle upgrade.
 * A class, not a lambda: the configuration cache stores this transformer and runs it after the
 * build, and a class keeps that independent of how Kotlin compiles lambdas.
 */
private class FailureText : Transformer<String, BuildWorkResult> {
    override fun transform(result: BuildWorkResult): String =
        result.failure.map { failure ->
            generateSequence(failure) { it.cause }.joinToString("\n") { it.message.orEmpty() }
        }.orElse("")
}

internal const val EXPLAIN_HEADER = "fluxo-kmp-conf derived settings (FLUXO_EXPLAIN):"
