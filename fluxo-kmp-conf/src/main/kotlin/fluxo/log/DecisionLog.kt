package fluxo.log

import fluxo.conf.FluxoKmpConfContext
import org.gradle.api.Project
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input

/**
 * Reports a setting the plugin derived for [project] instead of reading it from the consumer,
 * as `[<project path>] <setting> = <value> (<reason>; <how to change>)`.
 *
 * Info level, so a plain build stays quiet; `FLUXO_VERBOSE`, `MAX_DEBUG` and `--info` show it.
 * With `FLUXO_EXPLAIN` the line is also kept for one block printed when the build ends: that
 * block survives a configuration-cache hit, where configuration (and so this call) never runs.
 */
internal fun FluxoKmpConfContext.logDecision(
    project: Project,
    setting: String,
    value: Any?,
    reason: String,
    howToChange: String,
) {
    val line = "[${project.path}] $setting = $value ($reason; $howToChange)"
    project.logger.i(line)
    keepForExplain(line)
}

/**
 * Prints the `FLUXO_EXPLAIN` block at build end. A flow action, not a build service or a
 * listener: Gradle stores its parameters in the configuration-cache entry and runs it on a
 * cache hit too, while a listener registered during configuration is simply never registered.
 */
internal abstract class PrintDecisionsAction : FlowAction<PrintDecisionsAction.Parameters> {
    interface Parameters : FlowParameters {
        @get:Input
        val lines: ListProperty<String>
    }

    override fun execute(parameters: Parameters) {
        val lines = parameters.lines.get()
        val body = if (lines.isEmpty()) "  (none)" else lines.joinToString("\n") { "  $it" }
        Logging.getLogger(PrintDecisionsAction::class.java)
            .lifecycle("$EXPLAIN_HEADER\n$body")
    }
}

internal const val EXPLAIN_HEADER = "fluxo-kmp-conf derived settings (FLUXO_EXPLAIN):"
