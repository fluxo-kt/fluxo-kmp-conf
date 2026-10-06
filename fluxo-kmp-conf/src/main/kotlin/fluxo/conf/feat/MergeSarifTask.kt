package fluxo.conf.feat

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Merges every module's SARIF report of one tool (Android Lint, or Detekt of either line) into
 * one root report, so CI and code-scanning consumers read a single file.
 *
 * Not Detekt's `ReportMergeTask`: it keeps the first report's rule list and moves every result
 * under it, but Lint lists per report only the rules that fired and numbers them there
 * (`ruleIndex`), so merged results of other modules pointed at the wrong rule. It also exists
 * in two incompatible packages, one per Detekt line, while modules of one build may run either.
 *
 * Reports that don't exist are skipped: a task disabled for a `KMP_TARGETS`-filtered target, or
 * a consumer's `sarif.required = false`, writes none.
 *
 * With [failOnNewApi] (Lint) it then fails the build when Lint found a call above a module's
 * minSdk (`NewApi`): such a call crashes on older devices. Both in one task, after every
 * module's Lint: Lint runs with `abortOnError = false`, so each module reports instead of
 * stopping the build, and this task, last in `check`, lists every `NewApi` finding of the build
 * in one run. Lint's own switches turn it off, because they keep the finding out of the report:
 * `lint { disable += "NewApi" }`, `lint.xml`, or a baseline.
 *
 * Every module's report uses the root project as `%SRCROOT%` (Lint measured on AGP 9.4.1;
 * fluxo sets Detekt's `basePath` to the root), with root-relative paths, so results move
 * between runs unchanged.
 */
@CacheableTask
internal abstract class MergeSarifTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val input: ConfigurableFileCollection

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:Input
    abstract val failOnNewApi: Property<Boolean>

    @TaskAction
    fun merge() {
        @Suppress("UNCHECKED_CAST")
        val reports = input.files.filter { it.isFile }
            .map { JsonSlurper().parse(it) as MutableMap<String, Any?> }
        if (reports.isEmpty()) return
        val out = output.get().asFile
        out.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mergeSarif(reports))))
        if (!failOnNewApi.get()) return
        val newApi = findings(reports, NEW_API)
        if (newApi.isNotEmpty()) throw GradleException(newApiFailure(newApi, out))
    }
}

private const val NEW_API = "NewApi"

private typealias Json = MutableMap<String, Any?>

// SARIF read by JsonSlurper: objects are maps, arrays are lists; a missing key reads as absent.
@Suppress("UNCHECKED_CAST")
private fun Json.obj(key: String) = this[key] as Json?

@Suppress("UNCHECKED_CAST")
private fun Json.list(key: String) = this[key] as List<Json>? ?: emptyList()

private val Json.runs get() = list("runs")
private val Json.results get() = list("results")
private val Json.driver get() = obj("tool")?.obj("driver")

/**
 * One run holding every result. Rules are the union by id, in first-seen order, and each
 * result's `ruleIndex` is renumbered into it. The first report supplies everything else (tool,
 * `%SRCROOT%`), which is the same in every report of one tool in one build.
 */
internal fun mergeSarif(reports: List<Json>): Json {
    val ruleIndex = LinkedHashMap<String, Int>()
    val rules = ArrayList<Json>()
    val results = ArrayList<Json>()
    for (run in reports.flatMap { it.runs }) {
        for (rule in run.driver?.list("rules").orEmpty()) {
            val id = rule["id"] as String
            if (id !in ruleIndex) {
                rules.add(rule)
                ruleIndex[id] = rules.lastIndex
            }
        }
        for (result in run.results) {
            val index = ruleIndex[result["ruleId"]]
            if (index != null) result["ruleIndex"] = index else result.remove("ruleIndex")
            results += result
        }
    }
    val merged = reports[0]
    val run = merged.runs[0]
    run["results"] = results
    run.driver?.set("rules", rules)
    merged["runs"] = listOf(run)
    return merged
}

/**
 * `path:line: message` of each [ruleId] result, once: debug and release report the same call.
 */
internal fun findings(reports: List<Json>, ruleId: String): List<String> =
    reports.flatMap { it.runs }.flatMap { it.results }
        .filter { it["ruleId"] == ruleId }
        .map { result ->
            val location = result.list("locations").firstOrNull()?.obj("physicalLocation")
            val path = location?.obj("artifactLocation")?.get("uri") ?: "?"
            val line = location?.obj("region")?.get("startLine") ?: "?"
            "$path:$line: ${result.obj("message")?.get("text")?.toString().orEmpty()}"
        }
        .distinct()

private fun newApiFailure(findings: List<String>, report: File): String {
    val calls = if (findings.size == 1) "1 call" else "${findings.size} calls"
    return buildString {
        appendLine("Android Lint found $calls above minSdk ($NEW_API); they crash on old devices:")
        findings.forEach { appendLine("  $it") }
        appendLine("Fix: guard the call (`if (Build.VERSION.SDK_INT >= N)`, `@RequiresApi(N)`)")
        appendLine("or raise minSdk. To accept a call: add it to the Lint baseline")
        appendLine("(./gradlew updateLintBaseline). To stop this check:")
        appendLine("lint { disable += \"$NEW_API\" }.")
        append("Merged report: ${report.path}")
    }
}
