package fluxo.compat

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
    val projectDir = tempDir.resolve(row.getValue("id"))
    runConsumerCase(
        row,
        tempDir,
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
            """.trimIndent(),
        )
    }

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
}

private fun gradlePluginBuildScript(row: Map<String, String>): String =
    """
    plugins {
        id("org.jetbrains.kotlin.jvm") version "${row.getValue("kgpVersion")}"
        id("com.gradle.plugin-publish") version "${row.getValue("pluginPublishVersion")}"
        id("${pluginId()}") version "${pluginVersion()}"
    }

    group = "compat"
    version = "1.0.0"

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
