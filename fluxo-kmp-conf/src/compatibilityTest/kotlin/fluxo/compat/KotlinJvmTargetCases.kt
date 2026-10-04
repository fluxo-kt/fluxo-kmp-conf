package fluxo.compat

import java.io.DataInputStream
import java.nio.file.Path
import kotlin.io.path.inputStream

/** JVM target and JDK API cases of the Kotlin/JVM consumer fixture ([runKotlinJvmConsumer]). */
internal fun runKotlinJvmTargetCases(row: Map<String, String>, tempDir: Path) {
    runJvmTarget26Case(row, tempDir)
    runDefaultJvmTargetCase(row, tempDir)
    runJdkApiLimitCases(row, tempDir)
}

/**
 * The highest JVM target is whatever the consumer's Kotlin supports, never a table inside the
 * plugin: Kotlin 2.4 added target 26, so there 26 must reach the class files unchanged, and an
 * older Kotlin must reject an explicit 26 with the fix instead of quietly compiling to a lower
 * target. `useJdkRelease = false` keeps the JDK API limit out of this case: the fixture JDK
 * (17) has no API description for 26, which is a separate decision.
 */
private fun runJvmTarget26Case(row: Map<String, String>, tempDir: Path) {
    val supported = row.kgpMinor() >= FIRST_KOTLIN_WITH_JVM_26
    val (projectDir, output) = runKotlinJvmVariant(
        row,
        tempDir,
        "jvm26",
        "jvmTarget = \"26\"",
        "useJdkRelease = false",
        expectFailure = if (supported) emptyList() else listOf(JVM_TARGET_ABOVE_MAX),
    )
    if (!supported) return
    val major = projectDir.mainClassMajor()
    check(major == JAVA_26_CLASS_MAJOR) { "jvmTarget 26 compiled to major $major:\n$output" }
}

/**
 * A library that sets no JVM target must compile to 17 whatever JDK runs the build: a target that
 * followed the build JDK made bytecode depend on the machine (it sank v0.15.0's first tag). The
 * fixture rows run on JDK 17, where both behaviours agree, so this case runs on JDK 21.
 */
private fun runDefaultJvmTargetCase(row: Map<String, String>, tempDir: Path) {
    val (projectDir, output) = runKotlinJvmVariant(row, tempDir, "jdk21", jdk = JDK_21)
    val major = projectDir.mainClassMajor()
    check(major == JAVA_17_CLASS_MAJOR) { "Default library target compiled to $major:\n$output" }
}

/**
 * Code compiled for JVM 17 must not call JDK API newer than 17: it compiles, then fails with
 * NoSuchMethodError on a Java 17 runtime. Both compile JDKs above the target must reject it: the
 * JDK running Gradle, and a Java toolchain the consumer sets with the build on JDK 17 - the case
 * an equality check against the build JDK missed.
 */
private fun runJdkApiLimitCases(row: Map<String, String>, tempDir: Path) {
    val expect = listOf(USES_JDK_21_API_ERROR)
    runKotlinJvmVariant(
        row,
        tempDir,
        "jdk21",
        jdk = JDK_21,
        source = USES_JDK_21_API,
        expectFailure = expect,
    )
    runKotlinJvmVariant(
        row,
        tempDir,
        "toolchain21",
        "jvmTarget = \"17\"",
        // Gradle API form: fluxo applies the Kotlin plugin, so no `kotlin {}` accessor exists here.
        script = "configure<JavaPluginExtension> { " +
            "toolchain.languageVersion.set(JavaLanguageVersion.of($JDK_21)) }",
        source = USES_JDK_21_API,
        arguments = listOf(
            "-Porg.gradle.java.installations.paths=" + resolveCompatJdkHome(JDK_21).absolutePath,
            "-Porg.gradle.java.installations.auto-download=false",
        ),
        expectFailure = expect,
    )
}

/** `Thread.ofVirtual()` is JDK 21 API. */
private const val USES_JDK_21_API = "package compat\n\nfun virtualThreads() = Thread.ofVirtual()\n"

private const val USES_JDK_21_API_ERROR = "Unresolved reference 'ofVirtual'"

/** A class file starts with its magic number, minor version, then major version. */
private fun Path.mainClassMajor(): Int =
    DataInputStream(resolve(MAIN_CLASS).inputStream()).use {
        it.readInt()
        it.readUnsignedShort()
        it.readUnsignedShort()
    }

private const val MAIN_CLASS = "build/classes/kotlin/main/compat/CompatSubjectKt.class"

private const val JDK_21 = 21

private const val JAVA_17_CLASS_MAJOR = 61

private const val JVM_TARGET_ABOVE_MAX = "supports JVM targets up to"

private const val JAVA_26_CLASS_MAJOR = 70

/** The first Kotlin release whose `JvmTarget` has 26 (kotlin-compiler 2.4.10 `JvmTarget.kt`). */
@Suppress("MagicNumber")
private val FIRST_KOTLIN_WITH_JVM_26 = KotlinVersion(2, 4)
