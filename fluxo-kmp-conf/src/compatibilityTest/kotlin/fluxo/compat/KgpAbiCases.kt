package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

/** Every Kotlin line with its own ABI validation, each in its own DSL shape. */
internal val KGP_ABI_LINES = listOf("2.2.21", "2.3.21")

/**
 * Kotlin 2.2+ validates ABI with its own engine, no BCV declared. Its dumps must equal BCV's
 * byte for byte (consumers' committed BCV dumps keep passing), BCV's `apiDump`/`apiCheck`
 * names keep working, and `check` runs the Kotlin check. A build that applies BCV keeps BCV.
 */
internal fun runKmpKgpAbiCase(row: Map<String, String>, tempDir: Path, kgp: String) {
    val kgpRow = row + ("kgpVersion" to kgp)
    val projectDir = tempDir.resolve(row.getValue("id") + "-kgp-abi-$kgp")
    fun run(
        tasks: List<String>,
        bcv: Boolean,
        extraSource: String = "",
        expectFailure: List<String> = emptyList(),
        dryRun: Boolean = false,
    ) = runConsumerCase(
        kgpRow,
        tempDir,
        rootProjectName = "compat-kmp-abi",
        projectDir = projectDir,
        tasks = tasks,
        arguments = listOf("-PFLUXO_EXPLAIN=true"),
        expectFailure = expectFailure,
        assertTasksSucceed = !dryRun,
    ) {
        it.resolve("build.gradle.kts").writeText(kgpAbiBuildScript(kgpRow, bcv))
        it.resolve("src/commonMain/kotlin").createDirectories().resolve("A.kt")
            .writeText("package compat\n\nclass Api {\n    fun f(): Int = 1\n}\n$extraSource")
    }

    val bcvOutput = run(listOf("apiDump"), bcv = true).output
    check("ABI validation engine = BCV" in bcvOutput) { bcvOutput }
    val bcvDumps = projectDir.resolve("api").readTree()
    check(bcvDumps.keys.any { it.endsWith(".klib.api") } && bcvDumps.size >= 2) {
        "BCV wrote no JVM and klib dumps: ${bcvDumps.keys}\n$bcvOutput"
    }
    projectDir.resolve("api").toFile().deleteRecursively()

    val kgpOutput = run(listOf("apiDump"), bcv = false).output
    check("ABI validation engine = Kotlin Gradle plugin" in kgpOutput) { kgpOutput }
    val kgpDumps = projectDir.resolve("api").readTree()
    check(kgpDumps.keys == bcvDumps.keys && kgpDumps.all { (k, v) -> v == bcvDumps[k] }) {
        "Kotlin $kgp's dumps differ from BCV's, so committed BCV dumps would fail:\n" +
            kgpDumps.keys.union(bcvDumps.keys).joinToString("\n") { k ->
                "$k: kgp=${kgpDumps[k]}\nbcv=${bcvDumps[k]}"
            }
    }
    val plan = run(listOf("check", "--dry-run"), bcv = false, dryRun = true).output
    check(listOf(":checkKotlinAbi ", ":checkLegacyAbi ").any { it in plan }) {
        "check doesn't run Kotlin $kgp's ABI check"
    }
    run(
        listOf("apiCheck"),
        bcv = false,
        extraSource = "\nfun compatAdded(): Int = 1\n",
        expectFailure = listOf("compatAdded"),
    )
}

private fun Path.readTree(): Map<String, String> = Files.walk(this).use { paths ->
    paths.filter(Files::isRegularFile).toList()
        .associate { it.relativeTo(this).toString() to String(it.readBytes()) }
}

private fun kgpAbiBuildScript(row: Map<String, String>, bcv: Boolean) =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}"
        ${if (bcv) "id(\"org.jetbrains.kotlinx.binary-compatibility-validator\") version \"$BCV\"" else ""}
        id("${pluginId()}") version "${pluginVersion()}"
    }

    fkcSetupMultiplatform(
        config = {
            enablePublication = false
            enableGradleDoctor = false
            setupCoroutines = false
            enableApiValidation = true
        },
        kmp = {
            jvm()
            // A klib target that needs no Node.js: Kotlin 2.2 adds a project repository for it.
            linuxX64()
        },
    )
    """.trimIndent()

private const val BCV = "0.18.2"
