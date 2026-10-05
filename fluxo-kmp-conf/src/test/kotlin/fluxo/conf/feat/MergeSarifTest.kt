package fluxo.conf.feat

import groovy.json.JsonSlurper
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class MergeSarifTest {

    // Lint lists per report only the rules that fired, so the same rule has different indexes.
    private val app = report("NewApi", "app/A.kt", "InlinedApi", "app/A.kt")
    private val lib = report("InlinedApi", "lib/B.kt", "NewApi", "lib/B.kt")

    @Test
    fun `merged results keep pointing at their own rule`() {
        val run = mergeSarif(listOf(app(), lib())).list("runs").single()
        val rules = run.obj("tool").obj("driver").list("rules").map { it["id"] }
        val results = run.list("results")
        val pointedAt = results.map { rules[it["ruleIndex"].toString().toInt()] }
        assertEquals(results.map { it["ruleId"] }, pointedAt)
        assertEquals(4, results.size)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.obj(key: String) = this[key] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(key: String) = this[key] as List<Map<String, Any?>>

    @Test
    fun `NewApi findings are listed once per call across variants`() {
        val found = findings(listOf(app(), app(), lib()), "NewApi")
        assertEquals(listOf("app/A.kt:7: msg", "lib/B.kt:7: msg"), found)
    }

    /** A Lint-shaped SARIF report with two results whose rules are numbered in this order. */
    @Suppress("UNCHECKED_CAST")
    private fun report(rule0: String, path0: String, rule1: String, path1: String) = {
        JsonSlurper().parseText(
            """
            {"runs": [{"tool": {"driver": {"name": "Android Lint",
              "rules": [{"id": "$rule0"}, {"id": "$rule1"}]}},
             "results": [${result(rule0, 0, path0)}, ${result(rule1, 1, path1)}]}]}
            """,
        ) as MutableMap<String, Any?>
    }

    private fun result(rule: String, index: Int, path: String) =
        """{"ruleId": "$rule", "ruleIndex": $index, "message": {"text": "msg"},
            "locations": [{"physicalLocation": {"artifactLocation": {"uri": "$path"},
            "region": {"startLine": 7}}}]}"""
}
