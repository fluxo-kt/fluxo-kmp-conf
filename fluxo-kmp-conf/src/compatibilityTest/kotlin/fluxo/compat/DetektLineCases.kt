package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Detekt's type resolution must see the consumer's stdlib. Detekt 1.23 embeds Kotlin 2.0, which
 * can't read stdlib 2.2+ metadata: built-in types still resolve, stdlib functions don't, and the
 * rules needing them go silent with no warning. `RedundantHigherOrderMapUsage` on `List.map` is
 * such a rule (Detekt 1.23.8 CLI: found with stdlib 2.1.21, missing with 2.4.20; Detekt 2 finds
 * it with 2.4.20), on by default, and the code compiles without warnings. A row whose stdlib is
 * 2.2+ thus fails only if fluxo moved the module to Detekt 2.
 */
internal fun runDetektTypeResolutionCase(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-detekt-type-resolution",
        projectDir = tempDir.resolve(row.getValue("id") + "-detekt-type-resolution"),
        tasks = listOf("detektMain"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
        // Where fluxo moved the module to Detekt 2, its failure also explains the move.
        expectFailure = listOf("RedundantHigherOrderMapUsage") +
            if (row.kgpMinor() >= KotlinVersion(2, 2)) listOf("runs Detekt 2") else emptyList(),
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(
            markerKotlinJvmBuildScript(row)
                .replace("setupVerification = false", "setupVerification = true"),
        )
        val sources = Files.createDirectories(projectDir.resolve("src/main/kotlin/compat"))
        sources.resolve("Items.kt").writeText(
            "package compat\n\nfun countItems(items: List<Int>): Int = items.map { it }.size\n",
        )
    }
}
