package fluxo.compat

import java.io.DataInputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

/** JVM target and JDK API cases of the Kotlin/JVM consumer fixture ([kotlinJvmConsumerCases]). */
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
        javaSource = USES_JDK_21_API_JAVA,
        tasks = COMPILE_JAVA,
        expectFailure = listOf(USES_JDK_21_API_JAVA_ERROR),
    )
    // javac rejects --add-exports, --add-reads and --patch-module together with --release, so a
    // module passing them keeps compiling, unlimited. The consumer adds them after fkcSetup*().
    runKotlinJvmVariant(
        row,
        tempDir,
        "jdk21",
        jdk = JDK_21,
        script = "tasks.withType<JavaCompile>().configureEach { options.compilerArgs.addAll(" +
            "listOf(\"--add-exports\", \"java.base/jdk.internal.misc=ALL-UNNAMED\")) }",
        javaSource = USES_INTERNAL_JDK_API_JAVA,
        tasks = COMPILE_JAVA,
    )
    runKotlinJvmVariant(
        row,
        tempDir,
        "jdk21",
        jdk = JDK_21,
        source = USES_JDK_21_API,
        // The module sets no target, so the failure must point at the new library default.
        expectFailure = expect + DEFAULTED_TARGET_HINT,
    )
    runToolchainJdkApiLimitCases(row, tempDir)
    // The switch-off lifts both limits: the same calls compile at target 17 on JDK 21.
    runKotlinJvmVariant(
        row,
        tempDir,
        "jdk21",
        "jvmTarget = \"17\"",
        "useJdkRelease = false",
        jdk = JDK_21,
        source = USES_JDK_21_API,
        javaSource = USES_JDK_21_API_JAVA,
        tasks = listOf("compileKotlin") + COMPILE_JAVA,
    )
    // Test code keeps the full JDK API, so it can cover paths a runtime check enables on newer
    // JDKs; the limit stays on for main code in the same module.
    runKotlinJvmVariant(
        row,
        tempDir,
        "jdk21",
        "jvmTarget = \"17\"",
        jdk = JDK_21,
        source = USES_JDK_21_API,
        javaSource = USES_JDK_21_API_JAVA,
        sourceSet = "test",
        tasks = listOf("compileTestKotlin", "compileTestJava"),
    )
    runJdkClassesOnClasspathCases(row, tempDir)
    runTestJdkCase(row, tempDir)
}

/** `TEST_JDK=21` on a build running JDK 17: the tests themselves must run on 21. */
private fun runTestJdkCase(row: Map<String, String>, tempDir: Path) {
    runKotlinJvmVariant(
        row,
        tempDir,
        "testjdk",
        source = "package compat\n\nimport kotlin.test.Test\nimport kotlin.test.assertEquals\n\n" +
            "class TestJdkTest {\n    @Test\n    fun runsOnTestJdk() = " +
            "assertEquals(\"$JDK_21\", System.getProperty(\"java.specification.version\"))\n}\n",
        sourceSet = "test",
        tasks = listOf("test"),
        arguments = listOf("-PTEST_JDK=$JDK_21") + jdk21Installations(),
    )
    // Tests compiled for 21 can't load on 17: the build must say so before any test runs.
    runKotlinJvmVariant(
        row,
        tempDir,
        "testjdk",
        "jvmTarget = \"$JDK_21\"",
        jdk = JDK_21,
        tasks = listOf("test"),
        arguments = listOf("-PTEST_JDK=17"),
        expectFailure = listOf("TEST_JDK=17 is below JVM $JDK_21"),
    )
}

private fun jdk21Installations() = listOf(
    "-Dorg.gradle.java.installations.paths=" + resolveCompatJdkHome(JDK_21).absolutePath,
    "-Dorg.gradle.java.installations.auto-download=false",
)

/**
 * A jar with `java.*` classes on the Kotlin classpath (`android.jar` in shared JVM code) makes
 * kotlinc accept JDK methods newer than the target despite `-Xjdk-release`, so the build must
 * say so.
 */
private fun runJdkClassesOnClasspathCases(row: Map<String, String>, tempDir: Path) {
    val name = "jdk21"
    val projectDir = tempDir.resolve("${row.getValue("id")}-$name").createDirectories()
    ZipOutputStream(projectDir.resolve(JDK_STUB_JAR).outputStream()).use {
        // A real class: KGP's classpath snapshot parses every class file in the jar.
        val objectClass = "java/lang/Object.class"
        it.putNextEntry(ZipEntry(objectClass))
        checkNotNull(ClassLoader.getSystemResourceAsStream(objectClass)).use { c -> c.copyTo(it) }
        it.closeEntry()
    }
    // String form: fluxo applies the Kotlin plugin, so no `compileOnly` accessor exists here.
    val script = "dependencies { \"compileOnly\"(files(\"$JDK_STUB_JAR\")) }"
    val ci = listOf("-PCI=true")
    val (_, output) = runKotlinJvmVariant(
        row,
        tempDir,
        name,
        "jvmTarget = \"17\"",
        jdk = JDK_21,
        script = script,
        arguments = ci,
    )
    check(JDK_CLASSES_WARNING in output) { "No warning for $JDK_STUB_JAR:\n$output" }
    // CI compiles release and non-release alike, so a release build reuses the CI build's
    // output (up to date or from the build cache) and must still fail on the same classpath.
    runKotlinJvmVariant(
        row,
        tempDir,
        name,
        "jvmTarget = \"17\"",
        jdk = JDK_21,
        script = script,
        arguments = ci + "-PRELEASE=true",
        expectFailure = listOf(JDK_CLASSES_WARNING),
    )
}

private const val JDK_STUB_JAR = "jdk-stub.jar"

private const val JDK_CLASSES_WARNING = "$JDK_STUB_JAR on the classpath declares java.* classes"

/**
 * A JDK 21 Java toolchain with the build on JDK 17: both compilers' limits follow the compiler
 * the task uses, never the JDK running Gradle.
 */
private fun runToolchainJdkApiLimitCases(row: Map<String, String>, tempDir: Path) {
    // Gradle API form: fluxo applies the Kotlin plugin, so no `kotlin {}` accessor exists here.
    val toolchain = "configure<JavaPluginExtension> { " +
        "toolchain.languageVersion.set(JavaLanguageVersion.of($JDK_21)) }"
    val installations = jdk21Installations()
    val (_, explicitOutput) = runKotlinJvmVariant(
        row,
        tempDir,
        "toolchain21",
        "jvmTarget = \"17\"",
        script = toolchain,
        source = USES_JDK_21_API,
        arguments = installations,
        expectFailure = listOf(USES_JDK_21_API_ERROR),
    )
    // An explicit target is the consumer's choice, so the same failure gets no hint.
    check(DEFAULTED_TARGET_HINT !in explicitOutput) {
        "Hint printed for an explicit target:\n$explicitOutput"
    }
    runKotlinJvmVariant(
        row,
        tempDir,
        "toolchain21",
        "jvmTarget = \"17\"",
        script = toolchain,
        javaSource = USES_JDK_21_API_JAVA,
        tasks = COMPILE_JAVA,
        arguments = installations,
        expectFailure = listOf(USES_JDK_21_API_JAVA_ERROR),
    )
}

/** Start of the build-end hint for a failure in a module whose JVM target was defaulted. */
private const val DEFAULTED_TARGET_HINT = "sets no jvmTarget, so it compiles for JVM 17"

/** `Thread.ofVirtual()` is JDK 21 API. */
private const val USES_JDK_21_API = "package compat\n\nfun virtualThreads() = Thread.ofVirtual()\n"

private const val USES_JDK_21_API_ERROR = "Unresolved reference 'ofVirtual'"

private const val USES_JDK_21_API_JAVA =
    "package compat;\n\npublic class Extra {\n" +
        "    public static Object virtualThreads() { return Thread.ofVirtual(); }\n}\n"

private const val USES_JDK_21_API_JAVA_ERROR = "method ofVirtual()"

/** `jdk.internal.misc` is not exported by `java.base`; reaching it needs `--add-exports`. */
private const val USES_INTERNAL_JDK_API_JAVA =
    "package compat;\n\npublic class Extra {\n" +
        "    public static Object vm() { return jdk.internal.misc.VM.class; }\n}\n"

private val COMPILE_JAVA = listOf("compileJava")

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
