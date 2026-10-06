package fluxo.compat

import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipFile
import kotlin.io.path.writeText

private const val PLUGIN_ID = "compat.compat-plugin"
private const val PLUGIN_CLASS = "compat.CompatPlugin"

/**
 * A Gradle-plugin module set up with `fkcSetupGradlePlugin`. The jar's plugin descriptor is what
 * Gradle reads to apply a plugin by ID, so it must carry the requested ID, not the bare
 * declaration name (Gradle 9.4+ pre-fills a declaration's ID with its name).
 */
internal fun runGradlePluginConsumer(row: Map<String, String>, tempDir: Path) {
    buildAndCheckGradlePlugin(row, tempDir.resolve(row.getValue("id")))
    // A Kotlin newer than the running Gradle's embedded one is the case that breaks loading: the
    // newest row's Gradle embeds a Kotlin as new as its KGP, so it runs once more on the oldest
    // supported Gradle. A row that pins the language version has nothing to derive.
    if (row.getValue("kotlinLangVersion") == "-") {
        val oldGradle = row + ("gradleVersion" to OLDEST_SUPPORTED_GRADLE)
        buildAndCheckGradlePlugin(oldGradle, tempDir.resolve(row.getValue("id") + "-gradle-floor"))
    }
    if (row.kgpMinor() >= KGP_ABI_VALIDATION) runKgpAbiCase(row, tempDir)
}

/** The first Kotlin whose own ABI validation fluxo uses. */
private val KGP_ABI_VALIDATION = KotlinVersion(2, 4)

/**
 * On Kotlin 2.4+ ABI validation runs on the Kotlin Gradle plugin's engine, with no BCV declared:
 * BCV's `apiDump` writes the dump, `check` runs the KGP check, and `apiCheck` fails on an API
 * change the dump doesn't have.
 */
private fun runKgpAbiCase(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve(row.getValue("id") + "-kgp-abi")
    fun run(
        tasks: List<String>,
        extraSource: String = "",
        expectFailure: List<String> = emptyList(),
    ) = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-gradle-plugin-consumer",
        projectDir = projectDir,
        tasks = tasks,
        arguments = listOf("-PFLUXO_EXPLAIN=true"),
        expectFailure = expectFailure,
    ) {
        it.resolve("build.gradle.kts").writeText(
            gradlePluginBuildScript(row).replace("enableApiValidation = false", ""),
        )
        writeCompatPluginSource(it, extraSource)
    }

    val dumpOutput = run(listOf("apiDump")).output
    check("ABI validation engine = Kotlin Gradle plugin" in dumpOutput) { dumpOutput }
    val dump = projectDir.resolve("api/compat-gradle-plugin-consumer.api")
    check(Files.exists(dump) && "compat/CompatPlugin" in dump.toFile().readText()) {
        "apiDump wrote no dump with the plugin class at $dump\n$dumpOutput"
    }
    run(listOf("check")).assertTaskSuccess(":checkKotlinAbi")
    run(
        listOf("apiCheck"),
        extraSource = "\npublic fun compatAdded(): Int = 1\n",
        expectFailure = listOf("ABI has changed", "compatAdded"),
    )
}

private fun buildAndCheckGradlePlugin(row: Map<String, String>, projectDir: Path) {
    val output = runConsumerCase(
        row,
        projectDir.parent,
        rootProjectName = "compat-gradle-plugin-consumer",
        projectDir = projectDir,
    ) {
        it.resolve("build.gradle.kts").writeText(gradlePluginBuildScript(row))
        writeCompatPluginSource(it)
    }.output

    val jar = projectDir.resolve("build/libs").toFile().listFiles().orEmpty().single()
    val descriptor = ZipFile(jar).use { zip ->
        val names = zip.entries().asSequence().map { it.name }
            .filter { it.startsWith("META-INF/gradle-plugins/") && !it.endsWith("/") }.toList()
        check(names == listOf("META-INF/gradle-plugins/$PLUGIN_ID.properties")) {
            "Expected only the descriptor for '$PLUGIN_ID' in ${jar.name}, found $names"
        }
        Properties().apply { zip.getInputStream(zip.getEntry(names.single())).use(::load) }
    }
    check(descriptor.getProperty("implementation-class") == PLUGIN_CLASS) { "$descriptor" }

    // A Gradle plugin is loaded by the Gradle that runs it, whose embedded Kotlin reads metadata
    // only up to its own version, so the plugin must compile at that version or lower: the
    // embedded Kotlin of the building Gradle, capped by the consumer's Kotlin.
    val embedded = output.substringAfter(EMBEDDED_KOTLIN_MARKER).substringBefore('\n')
    val expected = listOf(embedded, row.getValue("kgpVersion"))
        .map { it.split('.').take(2).joinToString(".") }
        .minWith(compareBy({ it.substringBefore('.').toInt() }, { it.substringAfter('.').toInt() }))
    val metadata = ZipFile(jar).use { zip ->
        val entry = zip.entries().asSequence().single { it.name.endsWith(".kotlin_module") }
        // Header: the count of version ints, then the metadata version.
        DataInputStream(zip.getInputStream(entry)).use { data ->
            List(data.readInt()) { data.readInt() }.take(2).joinToString(".")
        }
    }
    check(metadata == expected) {
        "Gradle plugin compiled with Kotlin metadata $metadata; Gradle embeds Kotlin $embedded, " +
            "so it must be $expected or lower to load on the Gradle that built it.\n$output"
    }
}

private fun writeCompatPluginSource(projectDir: Path, extraSource: String = "") {
    val sources = projectDir.resolve("src/main/kotlin/compat")
    Files.createDirectories(sources)
    sources.resolve("CompatPlugin.kt").writeText(
        """
        package compat

        import org.gradle.api.Plugin
        import org.gradle.api.Project

        // `group` resolves only with sam-with-receiver: Gradle's Action has an implicit receiver.
        public class CompatPlugin : Plugin<Project> {
            override fun apply(target: Project) {
                target.tasks.register("compatHello") { group = "compat" }
            }
        }

        // A top-level declaration makes the compiler write META-INF/*.kotlin_module.
        internal fun compatGreeting(): String = "compat"
        """.trimIndent() + extraSource,
    )
}

private const val EMBEDDED_KOTLIN_MARKER = "FLUXO_COMPAT_EMBEDDED_KOTLIN="

/** The oldest Gradle the plugin supports (README "Targeted for"). */
private const val OLDEST_SUPPORTED_GRADLE = "9.0"

private fun gradlePluginBuildScript(row: Map<String, String>): String =
    """
    plugins {
        id("org.jetbrains.kotlin.jvm") version "${row.getValue("kgpVersion")}"
        id("com.gradle.plugin-publish") version "${row.getValue("pluginPublishVersion")}"
        id("${pluginId()}") version "${pluginVersion()}"
    }

    group = "compat"
    version = "1.0.0"

    // Build scripts run on Gradle's embedded Kotlin, so this is the version Gradle loads plugins with.
    println("$EMBEDDED_KOTLIN_MARKER" + KotlinVersion.CURRENT)

    fkcSetupGradlePlugin(pluginName = "compat-plugin", pluginClass = "$PLUGIN_CLASS") {
        setupVerification = false
        // On by default for Gradle plugins, and below Kotlin 2.4 it needs BCV declared by the
        // consumer; runKgpAbiCase covers it on 2.4+.
        enableApiValidation = false
        enablePublication = false
        enableGradleDoctor = false
        setupCoroutines = false
    }

    // fluxo applies sam-with-receiver to Gradle plugins. Its Gradle-side plugin runs against the
    // consumer's KGP API, so its jar must be that Kotlin version, never fluxo's own pin. (KGP
    // versions the compiler-side artifact itself, so the compiler classpath can't show this.)
    tasks.register("assertSamWithReceiverMatchesKotlin") {
        val kotlin = "${row.getValue("kgpVersion")}"
        val jar = plugins.single { it.javaClass.name.contains("SamWithReceiver") }
            .javaClass.protectionDomain.codeSource.location.path
        // Named `kotlin-sam-with-receiver-<version>[-gradle<N>].jar`, one variant per Gradle line.
        val jarVersion = jar.substringAfterLast('/').removeSuffix(".jar")
            .removePrefix("kotlin-sam-with-receiver-").substringBefore("-gradle")
        doLast {
            check(jarVersion == kotlin) {
                "sam-with-receiver Gradle plugin not on Kotlin " + kotlin + ": " + jar
            }
        }
    }
    """.trimIndent()
