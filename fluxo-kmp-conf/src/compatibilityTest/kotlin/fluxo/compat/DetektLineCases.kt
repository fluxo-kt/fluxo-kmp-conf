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

/**
 * `detektBaselineMerge` merges only what this build's baseline tasks wrote. A baseline task fluxo
 * disables (the experimental compilation's, whose sources `detektMain` analyses) still has its
 * file from an older build in `build/`, and merging it put findings for long-gone code into the
 * committed baseline, where nothing ever removes them.
 */
internal fun runDetektBaselineMergeCase(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve(row.getValue("id") + "-detekt-baseline-merge")
    fun merge(again: Boolean = false) = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-detekt-baseline-merge",
        projectDir = projectDir,
        tasks = listOf("detektBaselineMerge"),
        arguments = listOf("-PFLUXO_VERBOSE=true"),
        // A leftover that is not an input leaves the merge UP-TO-DATE.
        assertTasksSucceed = !again,
    ) {
        it.resolve("build.gradle.kts").writeText(
            markerKotlinJvmBuildScript(row).replace(
                "setupVerification = false",
                "setupVerification = true\n        experimentalLatestCompilation = true",
            ),
        )
        Files.createDirectories(it.resolve("src/main/kotlin/compat"))
            .resolve("Api.kt").writeText("package compat\n\nfun api(): Int = 1\n")
    }.output

    val disabled = Regex("task ':(\\w+)' disabled, detektMain analyses the same sources")
        .findAll(merge()).map { it.groupValues[1] }.filter { "Baseline" in it }.toSet()
    check(disabled.isNotEmpty()) { "fluxo disabled no duplicate Detekt baseline task" }
    val intermediates = Files.createDirectories(projectDir.resolve("build/intermediates/detekt"))
    for (task in disabled) {
        intermediates.resolve("baseline-$task.xml").writeText(
            "<?xml version=\"1.0\" ?>\n<SmellBaseline>\n  <ManuallySuppressedIssues/>\n" +
                "  <CurrentIssues>\n    <ID>$STALE_BASELINE_ID</ID>\n  </CurrentIssues>\n" +
                "</SmellBaseline>\n",
        )
    }
    merge(again = true)
    val merged = projectDir.resolve("detekt-baseline.xml")
    check(!Files.exists(merged) || STALE_BASELINE_ID !in String(Files.readAllBytes(merged))) {
        "detektBaselineMerge merged the leftover baseline of disabled $disabled"
    }
}

private const val STALE_BASELINE_ID = "CompatStaleFinding:Gone.kt:gone"
