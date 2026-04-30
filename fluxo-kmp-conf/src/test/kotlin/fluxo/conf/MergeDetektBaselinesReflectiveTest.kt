package fluxo.conf

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Element

/**
 * Falsification suite for [mergeDetektBaselines].
 *
 * Previous version of this file tested the reflective `BaselineProvider` load
 * path. That path was removed when [MergeDetektBaselinesTask] was rewritten to
 * parse the Detekt baseline XML format directly via JDK DOM/Transformer, which
 * removes all coupling to Detekt's isolated plugin classloader.
 *
 * Each test is shaped to falsify a distinct invariant of the merge algorithm:
 *   - union semantics (IDs from every input file appear in output)
 *   - deduplication (same ID present in N files appears exactly once)
 *   - section separation (manually-suppressed ≠ current-issues)
 *   - sort stability (output is lexicographically sorted per section)
 *   - empty-section tolerance (files with no IDs in a section round-trip cleanly)
 *
 * The tests use `@TempDir` temp files as the only Gradle-free entry point to
 * the extracted [mergeDetektBaselines] top-level function.
 */
internal class MergeDetektBaselinesReflectiveTest {

    @TempDir
    lateinit var tempDir: File

    private fun baseline(
        name: String,
        manual: Set<String> = emptySet(),
        current: Set<String> = emptySet(),
    ): File = File(tempDir, name).also { f ->
        val xml = buildString {
            appendLine("<?xml version='1.0' encoding='UTF-8'?>")
            appendLine("<SmellBaseline>")
            appendLine("  <ManuallySuppressedIssues>")
            for (id in manual) appendLine("    <ID>$id</ID>")
            appendLine("  </ManuallySuppressedIssues>")
            appendLine("  <CurrentIssues>")
            for (id in current) appendLine("    <ID>$id</ID>")
            appendLine("  </CurrentIssues>")
            appendLine("</SmellBaseline>")
        }
        f.writeText(xml)
    }

    private fun mergeFiles(vararg files: File): File {
        val out = File(tempDir, "merged.xml")
        mergeDetektBaselines(files.toList(), out)
        return out
    }

    // region union semantics

    @Test
    fun `IDs from all input files appear in the merged output`() {
        val a = baseline("a.xml", current = setOf("RuleA:FileA\$h1"))
        val b = baseline("b.xml", current = setOf("RuleB:FileB\$h2"))
        val out = mergeFiles(a, b).readText()
        assertTrue("RuleA:FileA" in out, "ID from file A missing")
        assertTrue("RuleB:FileB" in out, "ID from file B missing")
    }

    // endregion

    // region deduplication

    @Test
    fun `same ID present in multiple files appears exactly once in output`() {
        val shared = "SharedRule:File\$abc123"
        val a = baseline("a.xml", current = setOf(shared, "UniqueA:File\$u1"))
        val b = baseline("b.xml", current = setOf(shared, "UniqueB:File\$u2"))
        val out = mergeFiles(a, b).readText()
        assertEquals(
            1,
            Regex(Regex.escape(shared)).findAll(out).count(),
            "Duplicate ID must appear exactly once",
        )
    }

    // endregion

    // region section separation

    @Test
    fun `manually-suppressed IDs stay in ManuallySuppressedIssues, not in CurrentIssues`() {
        val a = baseline(
            "a.xml",
            manual = setOf("ManualRule:File\$m1"),
            current = setOf("CurrentRule:File\$c1"),
        )
        val out = mergeFiles(a).readText()
        val manualSection = out.substringAfter("<ManuallySuppressedIssues>")
            .substringBefore("</ManuallySuppressedIssues>")
        val currentSection = out.substringAfter("<CurrentIssues>")
            .substringBefore("</CurrentIssues>")

        assertTrue("ManualRule" in manualSection, "Manual ID must be in manual section")
        assertTrue("ManualRule" !in currentSection, "Manual ID must NOT be in current section")
        assertTrue("CurrentRule" in currentSection, "Current ID must be in current section")
        assertTrue("CurrentRule" !in manualSection, "Current ID must NOT be in manual section")
    }

    @Test
    fun `manual IDs from different files are NOT promoted to current issues`() {
        val a = baseline("a.xml", manual = setOf("SuppressedRule:File\$s1"))
        val b = baseline("b.xml", current = setOf("ActiveRule:File\$a1"))
        val out = mergeFiles(a, b).readText()
        val currentSection = out.substringAfter("<CurrentIssues>")
            .substringBefore("</CurrentIssues>")
        assertTrue(
            "SuppressedRule" !in currentSection,
            "Manually-suppressed rule must NOT leak into CurrentIssues",
        )
    }

    // endregion

    // region sort stability

    @Test
    fun `output IDs within each section are sorted lexicographically`() {
        val a = baseline(
            "a.xml",
            current = setOf("ZRule:File\$z", "ARule:File\$a", "MRule:File\$m"),
        )
        val out = mergeFiles(a).readText()
        val currentSection = out.substringAfter("<CurrentIssues>")
            .substringBefore("</CurrentIssues>")
        val ids = Regex("<ID>(.*?)</ID>").findAll(currentSection)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(ids.sorted(), ids, "IDs must be lexicographically sorted")
    }

    // endregion

    // region edge cases

    @Test
    fun `empty sections in input produce empty sections in output`() {
        val a = baseline("a.xml", current = setOf("RuleA:File\$a"))
        val out = mergeFiles(a)
        // Use DOM — string slicing misses self-closing empty elements
        // (e.g. <ManuallySuppressedIssues/>) which have no closing tag to bound against.
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(out)
        val root = doc.documentElement
        val children = root.childNodes
        var manualIds = 0
        for (i in 0 until children.length) {
            val node = children.item(i) ?: continue
            if (node.nodeName == "ManuallySuppressedIssues") {
                manualIds = (node as Element).getElementsByTagName("ID").length
            }
        }
        assertEquals(0, manualIds, "Empty manual section must have 0 ID children")
    }

    @Test
    fun `output is valid XML readable by DocumentBuilder`() {
        val a = baseline(
            "a.xml",
            current = setOf("RuleA:File\$a"),
            manual = setOf("RuleM:File\$m"),
        )
        val out = mergeFiles(a)
        // Parsing throws if not valid XML — no explicit assertion needed
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(out)
        assertEquals("SmellBaseline", doc.documentElement.nodeName)
    }

    // endregion
}
