package fluxo.log

import fluxo.conf.FluxoKmpConfContext
import org.gradle.api.Project

/**
 * Reports a setting the plugin derived for [project] instead of reading it from the consumer,
 * as `[<project path>] <setting> = <value> (<reason>; <how to change>)`.
 *
 * Info level, so a plain build stays quiet; `FLUXO_VERBOSE`, `MAX_DEBUG` and `--info` show it.
 * With `FLUXO_EXPLAIN` the line is also kept for one block printed when the build ends: that
 * block survives a configuration-cache hit, where configuration (and so this call) never runs.
 *
 * @see fluxo.conf.BuildEndReportAction
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
    buildEndReport.explain(line)
}
