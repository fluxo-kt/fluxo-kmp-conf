package fluxo.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompatibilityNewestRowsTest {

    private val rows = listOf(
        row("floor", kgp = "2.1.21", gradle = "9.0", agp = "-"),
        row("newest", kgp = "2.4.20", gradle = "9.8.0", agp = "-"),
        row("agp8", kgp = "2.1.21", gradle = "9.0", agp = "8.13.2"),
        row("agp9", kgp = "2.4.20", gradle = "9.8.0", agp = "9.4.1"),
    )

    @Test
    fun movesOnlyTheNewestValueOfEachColumnAndDropsUnmovedRows() {
        val moved = withNewest(rows, mapOf("kgpVersion" to "2.5.0", "agpVersion" to "9.5.0-rc01"))
        assertEquals(listOf("newest", "agp9"), moved.map { it.getValue("id") })
        assertEquals(listOf("2.5.0", "2.5.0"), moved.map { it.getValue("kgpVersion") })
        assertEquals(listOf("-", "9.5.0-rc01"), moved.map { it.getValue("agpVersion") })
        assertEquals(listOf("9.8.0", "9.8.0"), moved.map { it.getValue("gradleVersion") })
    }

    @Test
    fun comparesVersionPartsAsNumbers() {
        // "9.10" sorts below "9.8.0" as text; as numbers it is the newest.
        val withNewer = rows + row("next", kgp = "2.4.20", gradle = "9.10", agp = "-")
        val moved = withNewest(withNewer, mapOf("gradleVersion" to "9.11.0"))
        assertEquals(listOf("next"), moved.map { it.getValue("id") })
    }

    private fun row(id: String, kgp: String, gradle: String, agp: String) = mapOf(
        "id" to id,
        "kgpVersion" to kgp,
        "gradleVersion" to gradle,
        "agpVersion" to agp,
    )
}
