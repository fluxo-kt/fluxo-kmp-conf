package fluxo.conf

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowScope
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
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
    private val rootProject: Project,
    private val explain: Boolean,
) {
    private val warnings = ConcurrentLinkedQueue<String>()
    private val decisions = ConcurrentLinkedQueue<String>()
    private val registered = AtomicBoolean()

    init {
        // The FLUXO_EXPLAIN block prints even when no module derived anything.
        if (explain) register()
    }

    /** Prints [message] as a warning when the build ends, also on a configuration-cache hit. */
    fun warn(message: String) {
        register()
        warnings += message
    }

    /** Keeps [line] for the `FLUXO_EXPLAIN` block; a no-op without the flag. */
    fun explain(line: String) {
        if (explain) decisions += line
    }

    private fun register() {
        if (!registered.compareAndSet(false, true)) return
        // An explicit `Action`, not a trailing lambda: Detekt 1.23.8's IgnoredReturnValue
        // crashes the whole analysis (NPE in `findPackage()`) on the SAM-adapted call.
        flowScope.always(
            BuildEndReportAction::class.java,
            Action {
                parameters.warnings.set(rootProject.provider { warnings.toList() })
                parameters.explain.set(explain)
                parameters.decisions.set(rootProject.provider { decisions.toList() })
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
    }

    override fun execute(parameters: Parameters) {
        val logger = Logging.getLogger(BuildEndReportAction::class.java)
        parameters.warnings.get().forEach(logger::warn)
        if (parameters.explain.get()) {
            val lines = parameters.decisions.get()
            val body = if (lines.isEmpty()) "  (none)" else lines.joinToString("\n") { "  $it" }
            logger.lifecycle("$EXPLAIN_HEADER\n$body")
        }
    }
}

internal const val EXPLAIN_HEADER = "fluxo-kmp-conf derived settings (FLUXO_EXPLAIN):"
