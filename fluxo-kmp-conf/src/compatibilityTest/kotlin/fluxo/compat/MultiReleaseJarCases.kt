package fluxo.compat

import java.io.DataInputStream
import java.lang.module.ModuleFinder
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText

/**
 * A `src/jvm<N>Main` source set lands in `META-INF/versions/N` of the JVM jar. The JDK itself
 * judges the result, in this process: a [JarFile] opened at the runtime version resolves the
 * class to its JVM 11 variant only in a valid multi-release jar (manifest entry included), and
 * [ModuleFinder] reads the module from `module-info.class` under `versions/9`.
 */
internal fun runKmpMultiReleaseJarCase(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve(row.getValue("id") + "-multi-release")
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-multi-release",
        projectDir = projectDir,
        tasks = listOf("jvmJar"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { dir ->
        dir.resolve("build.gradle.kts").writeText(multiReleaseBuildScript(row))
        dir.writeSource("jvmMain/kotlin/compat/Jdk.kt", jdkObject(level = "BASE"))
        dir.writeSource(
            "jvmMain/kotlin/compat/Base.kt",
            "package compat\n\ninternal const val BASE = 8\n",
        )
        // Versioned code replaces an internal implementation as a rule, so main's internals count.
        dir.writeSource("jvm11Main/kotlin/compat/Jdk.kt", jdkObject(level = "BASE + 3"))
        dir.writeSource(
            "jvm9Main/java/module-info.java",
            "/* A comment naming module fake { */\n" +
                "module compat.mr {\n    requires kotlin.stdlib;\n    exports compat;\n}\n",
        )
    }

    val jar = projectDir.resolve("build/libs").listDirectoryEntries("*-jvm*.jar").single()
    JarFile(jar.toFile(), true, ZipFile.OPEN_READ, Runtime.version()).use { file ->
        val entry = checkNotNull(file.getJarEntry("compat/Jdk.class")) { "No compat/Jdk.class" }
        val major = DataInputStream(file.getInputStream(entry)).use {
            it.readInt()
            it.readUnsignedShort()
            it.readUnsignedShort()
        }
        check(major == JAVA_11_CLASS_MAJOR) {
            "The JDK resolved compat/Jdk.class to major $major, not the versions/11 variant " +
                "($JAVA_11_CLASS_MAJOR): ${file.entries().toList().map { it.name }}"
        }
    }
    val module = ModuleFinder.of(jar).findAll().single().descriptor().name()
    check(module == "compat.mr") { "The jar's module is $module, not compat.mr" }
}

private fun Path.writeSource(path: String, text: String) {
    val file = resolve("src/$path")
    file.parent.createDirectories()
    file.writeText(text)
}

private fun jdkObject(level: String) =
    "package compat\n\nobject Jdk {\n    fun level() = $level\n}\n"

private const val JAVA_11_CLASS_MAJOR = 55

private fun multiReleaseBuildScript(row: Map<String, String>) =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}"
        id("${pluginId()}") version "${pluginVersion()}"
    }

    fkcSetupMultiplatform(
        config = {
            setupVerification = false
            enablePublication = false
            enableGradleDoctor = false
            setupCoroutines = false
            jvmTarget = "1.8"
        },
        kmp = { jvm() },
    )
    """.trimIndent()
