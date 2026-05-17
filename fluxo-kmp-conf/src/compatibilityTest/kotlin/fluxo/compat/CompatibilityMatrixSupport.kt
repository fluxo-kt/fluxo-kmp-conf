package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path

internal fun selectedRows(fixture: String): List<Map<String, String>> {
    val profile = System.getProperty("compat.profile", "pr")
    val profiles = when (profile) {
        "release" -> setOf("pr", "release")
        "full" -> setOf("pr", "full")
        else -> setOf(profile)
    }
    val rows = matrixRows()
        .filter { it["fixture"] == fixture && it["profile"] in profiles }
    check(rows.isNotEmpty()) {
        "No $fixture compatibility rows selected for compat.profile=$profile"
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
    return lines.drop(1)
        .map { header.zip(it.split('\t')).toMap() }
}
