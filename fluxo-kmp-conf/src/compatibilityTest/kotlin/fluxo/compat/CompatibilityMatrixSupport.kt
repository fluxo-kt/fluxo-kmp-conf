package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path

internal fun selectedRows(fixture: String): List<Map<String, String>> {
    val rows = matrixRows().filter { it["fixture"] == fixture }
    // A run against the newest upstream versions keeps only the rows it moved, so a fixture
    // with floor rows only has none there.
    check(rows.isNotEmpty() || NEWEST_OVERRIDES.isNotEmpty()) {
        "No compatibility rows for fixture '$fixture' in compat/matrix.tsv"
    }
    return rows
}

internal fun selectedRows(vararg fixtures: String): List<Map<String, String>> =
    fixtures.flatMap(::selectedRows)

internal fun matrixRows(): List<Map<String, String>> {
    val root = Path.of(System.getProperty("fluxo.repo.root"))
    val lines = Files.readAllLines(root.resolve("compat/matrix.tsv"))
        .filter { it.isNotBlank() && !it.startsWith("#") }
    val header = lines.first().split('\t')
    val rows = lines.drop(1)
        .map { header.zip(it.split('\t')).toMap() }
    return if (NEWEST_OVERRIDES.isEmpty()) rows else withNewest(rows, NEWEST_OVERRIDES)
}

/**
 * The weekly run against the newest upstream releases (`.github/workflows/upstream-newest.yml`)
 * passes `FLUXO_COMPAT_NEWEST=kgpVersion=2.5.0,gradleVersion=9.9.0,…`. For each named column,
 * every row holding that column's newest value in the matrix gets the given version instead,
 * so the newest line moves forward as one, and only rows that moved are kept: the floor rows
 * already run on every change.
 */
internal fun withNewest(
    rows: List<Map<String, String>>,
    overrides: Map<String, String>,
): List<Map<String, String>> {
    val newest = overrides.keys.associateWith { column ->
        rows.mapNotNull { it[column]?.takeIf { v -> v != "-" } }.maxWithOrNull(::compareVersions)
    }
    return rows.mapNotNull { row ->
        val moved = overrides.filterKeys { column -> row[column] == newest[column] }
        if (moved.isEmpty()) null else row + moved
    }
}

private val NEWEST_OVERRIDES: Map<String, String> =
    System.getenv("FLUXO_COMPAT_NEWEST").orEmpty().split(',')
        .filter { it.isNotBlank() }
        .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

/** Numeric parts compare as numbers (`9.10` > `9.8`); a missing part counts as 0. */
private fun compareVersions(a: String, b: String): Int {
    val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val c = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}
