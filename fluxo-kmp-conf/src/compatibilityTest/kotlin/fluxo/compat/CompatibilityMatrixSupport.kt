package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path

internal fun selectedRows(fixture: String): List<Map<String, String>> {
    val rows = matrixRows().filter { it["fixture"] == fixture }
    check(rows.isNotEmpty()) { "No compatibility rows for fixture '$fixture' in compat/matrix.tsv" }
    return rows
}

internal fun selectedRows(vararg fixtures: String): List<Map<String, String>> =
    fixtures.flatMap(::selectedRows)

internal fun matrixRows(): List<Map<String, String>> {
    val root = Path.of(System.getProperty("fluxo.repo.root"))
    val lines = Files.readAllLines(root.resolve("compat/matrix.tsv"))
        .filter { it.isNotBlank() && !it.startsWith("#") }
    val header = lines.first().split('\t')
    return lines.drop(1)
        .map { header.zip(it.split('\t')).toMap() }
}
