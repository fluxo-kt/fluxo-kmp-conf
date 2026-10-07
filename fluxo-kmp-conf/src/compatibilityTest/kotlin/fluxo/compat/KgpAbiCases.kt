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
internal fun runKgpAbiCase(row: Map<String, String>, tempDir: Path, kgp: String, jvm: Boolean) {
    val kgpRow = row + ("kgpVersion" to kgp)
    val kind = if (jvm) "jvm" else "kmp"
    val projectDir = tempDir.resolve(row.getValue("id") + "-kgp-abi-$kind-$kgp")
    val sourceDir = if (jvm) "src/main/kotlin" else "src/commonMain/kotlin"
    fun run(
        tasks: List<String>,
        bcv: Boolean,
        extraSource: String = "",
        expectFailure: List<String> = emptyList(),
        dryRun: Boolean = false,
    ) = runConsumerCase(
        kgpRow,
        tempDir,
        rootProjectName = "compat-$kind-abi",
        projectDir = projectDir,
        tasks = tasks,
        arguments = listOf("-PFLUXO_EXPLAIN=true"),
        expectFailure = expectFailure,
        assertTasksSucceed = !dryRun,
    ) {
        it.resolve("build.gradle.kts").writeText(kgpAbiBuildScript(kgpRow, bcv, jvm))
        it.resolve(sourceDir).createDirectories().resolve("A.kt")
            .writeText("package compat\n\nclass Api {\n    fun f(): Int = 1\n}\n$extraSource")
    }

    if (jvm) {
        // Engine parity with BCV is proven by the KMP variant; here only the adapter must work.
        val output = run(listOf("apiDump"), bcv = false).output
        check("ABI validation engine = Kotlin Gradle plugin" in output) { output }
        check(projectDir.resolve("api").readTree().size == 1) { "no JVM dump:\n$output" }
        return
    }
    val bcvOutput = run(listOf("apiDump"), bcv = true).output
    check("ABI validation engine = BCV" in bcvOutput) { bcvOutput }
    val bcvDumps = projectDir.resolve("api").readTree()
    check(bcvDumps.keys.any { it.endsWith(".klib.api") }) {
        "BCV wrote no klib dump: ${bcvDumps.keys}\n$bcvOutput"
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

private fun kgpAbiBuildScript(row: Map<String, String>, bcv: Boolean, jvm: Boolean): String {
    val flags = "enablePublication = false; enableGradleDoctor = false; " +
        "setupCoroutines = false; enableApiValidation = true"
    val setup = if (jvm) {
        "fkcSetupKotlin { $flags }"
    } else {
        // A klib target that needs no Node.js: Kotlin 2.2 adds a project repository for it.
        "fkcSetupMultiplatform(config = { $flags }, kmp = { jvm(); linuxX64() })"
    }
    val kotlinId = "org.jetbrains.kotlin." + if (jvm) "jvm" else "multiplatform"
    return """
    plugins {
        id("$kotlinId") version "${row.getValue("kgpVersion")}"
        ${if (bcv) "id(\"org.jetbrains.kotlinx.binary-compatibility-validator\") version \"$BCV\"" else ""}
        id("${pluginId()}") version "${pluginVersion()}"
    }

    $setup
    """.trimIndent()
}

private const val BCV = "0.18.2"
