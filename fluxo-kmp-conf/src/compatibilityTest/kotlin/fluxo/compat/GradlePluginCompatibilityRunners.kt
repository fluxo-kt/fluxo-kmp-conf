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
}

private fun buildAndCheckGradlePlugin(row: Map<String, String>, projectDir: Path) {
    val output = runConsumerCase(
        row,
        projectDir.parent,
        rootProjectName = "compat-gradle-plugin-consumer",
        projectDir = projectDir,
    ) {
        it.resolve("build.gradle.kts").writeText(gradlePluginBuildScript(row))
        val sources = it.resolve("src/main/kotlin/compat")
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
            """.trimIndent(),
        )
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
        // On by default for Gradle plugins; BCV must then be declared by the consumer.
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
