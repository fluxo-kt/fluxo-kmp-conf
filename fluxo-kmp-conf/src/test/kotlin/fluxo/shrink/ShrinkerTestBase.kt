package fluxo.shrink

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import fluxo.gradle.normalizedPath as np
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.text.appendLine as ln
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import proguard.Configuration
import proguard.ConfigurationParser
import proguard.ProGuard

// TODO: Optimized bytcode tests.

/**
 * Foundation for R8 and ProGuard behavioral tests.
 *
 * Theese are pretty slow integration tests, *not* unit tests.
 * But they can ensure correctness of the minification rules.
 */
@Tag("slow")
@Tag("integration")
@OptIn(ExperimentalCompilerApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Suppress("LongParameterList", "LongMethod", "UnnecessaryAbstractClass", "ReturnCount")
internal abstract class ShrinkerTestBase {

    // region state

    private val compileResults = ConcurrentHashMap<String, JvmCompilationResult>()

    private lateinit var tempDir: Path

    @BeforeAll
    fun setUp() {
        tempDir = Files.createTempDirectory("junit." + this.javaClass.simpleName)
    }

    @AfterAll
    fun tearDown() {
        compileResults.clear()
        tempDir.toFile().deleteRecursively()
    }

    // endregion


    // region utility

    protected fun assertSeeds(
        @Language("pro") rules: String,
        @Language("kotlin") code: String = KCLASS_CODE,
        @Language("seed") expected: String? = null,
        @Language("seed") expectedR8: String? = expected,
        @Language("seed") expectedR8Compat: String? = expectedR8,
        @Language("seed") expectedProGuard: String? = expected,
        tempDir: Path = this.tempDir,
        addNoArgConstructorForR8Compat: Boolean = true,
        sort: Boolean = true,
    ) {
        val assertions = mutableListOf<() -> Unit>()

        expectedProGuard?.let {
            assertions += {
                val result = shrink(
                    code = code,
                    rules = rules,
                    tempDir = tempDir,
                    shrinker = Shrinker.ProGuard,
                )
                assertEquals(
                    expected = it.seeds(sort = sort),
                    actual = result.actualSeeds(sort),
                    message = "ProGuard seeds",
                )
            }
        }

        expectedR8?.let {
            assertions += {
                val result = shrink(
                    code = code,
                    rules = rules,
                    tempDir = tempDir,
                    shrinker = Shrinker.R8,
                    r8FullMode = true,
                )
                assertEquals(
                    expected = it.seeds(sort = sort, r8 = true),
                    actual = result.actualSeeds(sort),
                    message = "R8 full-mode seeds",
                )
            }
        }

        expectedR8Compat?.let {
            assertions += {
                val result = shrink(
                    code = code,
                    rules = rules,
                    tempDir = tempDir,
                    shrinker = Shrinker.R8,
                    r8FullMode = false,
                )
                var expect = it.trimIndent().trim()
                expect = when {
                    !addNoArgConstructorForR8Compat -> expect
                    // R8 compat always keeps no-argument constructor for kept classes!
                    else -> addNoArgConstructorsForR8Compat(expect)
                }
                assertEquals(
                    expected = expect.seeds(sort = sort, r8 = true),
                    actual = result.actualSeeds(sort),
                    message = "R8 compat-mode seeds",
                )
            }
        }

        assertAll(assertions)
    }

    // R8 compat always keeps no-argument constructor for kept classes!
    private fun addNoArgConstructorsForR8Compat(expect: String): String {
        val lines = expect.lines()
        val classes = lines.filterTo(ArrayList()) { c ->
            c.none { it.isWhitespace() || it == ':' || it == '(' } &&
                c.isNotBlank()
        }
        if (classes.isEmpty()) {
            return expect
        }
        var added: String? = null
        for (clazz in classes) {
            val simpleName = clazz.substringAfterLast('.')
            var noArgConstructorLine = "$clazz: $simpleName()"
            if (noArgConstructorLine in lines) {
                continue
            }
            noArgConstructorLine += '\n'
            when (added) {
                null -> added = noArgConstructorLine
                else -> added += noArgConstructorLine
            }
        }
        if (added == null) {
            return expect
        }
        return added + expect
    }

    private fun String.seeds(
        sort: Boolean = true,
        r8: Boolean = false,
    ): String {
        var seeds = trimIndent().trim()
        if (sort) {
            seeds = seeds.lines().sorted().joinToString("\n")
        }
        // NOTE: R8 doesn't save static class constructor `<clinit>` or doesn't show it in seeds!
        if (r8) {
            seeds = seeds.replace(CLINIT_REGEX, "")
        }
        // Help copy-pasting in raw string templates
        seeds = seeds.replace("$", "\${D}")
        return '\n' + seeds
    }

    private fun ShrinkingResult.actualSeeds(sort: Boolean): String =
        seeds.seeds(sort = sort)

    private fun shrink(
        @Language("kotlin") code: String,
        @Language("pro") rules: String,
        shrinker: Shrinker = Shrinker.ProGuard,
        fileName: String = DEFAULT_FILE_NAME,
        shrink: Boolean = true,
        optimize: Boolean = false,
        obfuscate: Boolean = false,
        r8FullMode: Boolean = false,
        tempDir: Path = this.tempDir,
    ): ShrinkingResult {
        val result = compileCode(name = fileName, contents = code)
        val filesDir = result.outputDirectory

        val confDir = Files.createTempDirectory(tempDir, "$shrinker-shrink").toFile()
        val mapping = confDir.resolve("mapping.txt")
        val seeds = confDir.resolve("seeds.txt")
        val usage = confDir.resolve("usage.txt")
        val finalConfig = confDir.resolve("final-config.pro")
        val rootConf = confDir.resolve("root-config.pro")

        rootConf.bufferedWriter().use { writer ->
            writer.ln("${rules.trimIndent().trim()}\n")

            writer.ln("-dontwarn java.lang.*")
            writer.ln("-dontwarn kotlin.*")
            writer.ln("-dontwarn kotlin.jvm.**")
            writer.ln("-dontwarn org.jetbrains.annotations.*")
            writer.ln("-dontnote kotlin.**")
            writer.ln("-verbose")

            writer.ln("-repackageclasses")
            writer.ln("-overloadaggressively")
            writer.ln("-allowaccessmodification")
            writer.ln("-mergeinterfacesaggressively")

            if (!optimize) {
                writer.ln("-dontoptimize")
            }
            if (!shrink) {
                writer.ln("-dontshrink")
            }
            if (!obfuscate) {
                writer.ln("-dontobfuscate")
            }

            when (shrinker) {
                Shrinker.ProGuard -> {
                    writer.ln("-optimizationpasses 1")
                    writer.ln("-optimizations !class/merging/horizontal")
                    writer.ln("-optimizeaggressively")
                    writer.ln("-skipnonpubliclibraryclasses")
                    writer.ln("-injars '${filesDir.np()}'")
                }

                Shrinker.R8 -> {
                    // Only archive types are supported by R8, e.g., .jar, .zip.
                    // Pack filesDir into a zip archive.
                    writer.ln("-injars '${getZippedDir(filesDir).np()}'")
                }
            }

            writer.ln("-printmapping '${mapping.np()}'")
            writer.ln("-printseeds '${seeds.np()}'")
            writer.ln("-printusage '${usage.np()}'")
            writer.ln("-printconfiguration '${finalConfig.np()}'")
        }

        runShrinker(shrinker) {
            when (shrinker) {
                Shrinker.ProGuard -> {
                    val configuration = Configuration()
                    ConfigurationParser(rootConf, System.getProperties()).use {
                        it.parse(configuration)
                    }
                    ProGuard(configuration)
                        .execute()
                }

                Shrinker.R8 -> {
                    val r = R8Command.builder()
                    r.mode = CompilationMode.RELEASE
                    r.proguardCompatibility = !r8FullMode // full-mode
                    r.setDisableTreeShaking(!shrink)
                    r.setDisableMinification(!obfuscate)
                    r.addProguardConfigurationFiles(rootConf.toPath())
                    // r.addLibraryFiles(Path.of(System.getProperty("java.home")))
                    confDir.resolve("out").let {
                        it.mkdirs()
                        r.setOutput(it.toPath(), OutputMode.ClassFile)
                    }
                    R8.run(r.build())
                }
            }
        }

        return object : ShrinkingResult {
            override val mapping: String by lazy(mapping::readText)
            override val seeds: String by lazy(seeds::readText)
            override val usage: String by lazy(usage::readText)
            override val finalConfig: String by lazy(finalConfig::readText)
        }
    }

    @Suppress("NestedBlockDepth")
    private fun getZippedDir(filesDir: File): File {
        val zip = filesDir.parentFile.resolve(filesDir.name + ".zip")
        if (!zip.exists()) {
            ZipOutputStream(zip.outputStream()).use { os ->
                for (file in filesDir.walkTopDown()) {
                    if (!file.isFile) continue
                    val zipEntry = ZipEntry(file.relativeTo(filesDir).path)
                    os.putNextEntry(zipEntry)
                    file.inputStream().use { it.copyTo(os) }
                    os.closeEntry()
                }
            }
        }
        return zip
    }

    private fun <R> runShrinker(
        shrinker: Shrinker,
        out: ByteArrayOutputStream = ByteArrayOutputStream(),
        replaceStdout: Boolean = true,
        cb: () -> R,
    ): R {
        val origOut = System.out
        val origErr = System.err
        try {
            return if (replaceStdout) {
                PrintStream(out).use {
                    System.setOut(it)
                    System.setErr(it)
                    cb()
                }
            } else {
                cb()
            }
        } catch (e: Throwable) {
            val message = buildString {
                ln("$shrinker failed")
                ln("Output:")
                ln(out.toString(Charsets.UTF_8).trim())
                ln(e)
            }
            throw IllegalStateException(message, e)
        } finally {
            if (replaceStdout) {
                System.setOut(origOut)
                System.setErr(origErr)
            }
        }
    }

    protected fun compileCode(
        name: String = DEFAULT_FILE_NAME,
        @Language("kotlin") contents: String = KCLASS_CODE,
        trimIndent: Boolean = true,
        stdout: Boolean = false,
        verbose: Boolean = false,
        tempDir: Path? = this.tempDir,
    ): JvmCompilationResult {
        return compileResults.computeIfAbsent("$name::$contents") {
            val kotlinSource = SourceFile.kotlin(
                name = name,
                contents = contents,
                trimIndent = trimIndent,
            )
            compileCode(kotlinSource, stdout = stdout, verbose = verbose, tempDir = tempDir)
        }
    }

    private fun compileCode(
        vararg source: SourceFile,
        stdout: Boolean = false,
        verbose: Boolean = false,
        tempDir: Path? = this.tempDir,
    ): JvmCompilationResult {
        val compilation = KotlinCompilation().apply {
            this.verbose = verbose

            sources = source.asList()

            if (tempDir != null) {
                workingDir = Files.createTempDirectory(tempDir, "kt-compile").toFile()
            }
            if (stdout) {
                messageOutputStream = System.out // see diagnostics in real time
            }
        }
        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        return result
    }

    private interface ShrinkingResult {
        val mapping: String
        val seeds: String
        val usage: String
        val finalConfig: String
    }

    protected companion object {
        @JvmStatic
        protected val D = '$'

        private const val DEFAULT_FILE_NAME = "KClass.kt"

        // R8 doesn't save static class constructor `<clinit>` or doesn't show it in seeds!
        @JvmStatic
        protected val CLINIT_REGEX = "(?im)\n?^[ \t]*[^\\s]+: void <clinit>\\(\\)".toRegex()

        /**
         * Default class for shrinker testing.
         * Has diverse language things inside.
         *
         * @see KCLASS_SEEDS
         */
        @JvmStatic
        protected val KCLASS_CODE = """
            @Suppress("UNUSED_PARAMETER", "RedundantSuppression", "MayBeConstant")
            abstract class KClass
            @JvmOverloads
            constructor(
                @JvmField
                var i: Int = 0,
                @Volatile
                private var l: Long = 0,
            ) {
                protected constructor(sa: Array<String>) : this()
                internal constructor(b: BooleanArray) : this()
                private constructor(s: String) : this()
                @Strictfp
                @JvmSynthetic
                fun bar(x: Byte, y: Float): Double { return y * x.toDouble() }
                fun bar(a: String, b: Byte, c: IntArray): Int { return a.length * b.toInt() + c.size }
                @Transient
                open var s: String? = null
                protected fun foo() {}
                abstract fun baz(a: IntArray): LongArray
                @Synchronized
                private fun bazShort(a: Array<String>): Array<Short> = Array(a.size) { 0 }
                @JvmName("fooString")
                fun bazString(s: String): String = s

                companion object {
                    const val CONST = 42
                    @Suppress("something")
                    val FIELD = "S"
                    @JvmField
                    protected val JVM_FIELD = "S"
                    @JvmStatic
                    val STATIC_FIELD = "S"
                    @JvmStatic
                    fun staticCoMethod() {}
                }
            }
        """.trimIndent()

        /**
         * @see KCLASS_CODE
         */
        @JvmStatic
        protected val KCLASS_SEEDS = """
            KClass
            KClass: KClass${D}Companion Companion
            KClass: KClass()
            KClass: KClass(boolean[])
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass(java.lang.String)
            KClass: KClass(java.lang.String[])
            KClass: double bar(byte,float)
            KClass: int CONST
            KClass: int bar(java.lang.String,byte,int[])
            KClass: int i
            KClass: java.lang.Short[] bazShort(java.lang.String[])
            KClass: java.lang.String FIELD
            KClass: java.lang.String JVM_FIELD
            KClass: java.lang.String STATIC_FIELD
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String fooString(java.lang.String)
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: java.lang.String s
            KClass: long l
            KClass: long[] baz(int[])
            KClass: void <clinit>()
            KClass: void foo()
            KClass: void setS(java.lang.String)
            KClass: void staticCoMethod()
        """.trimIndent()

        /**
         * @see KCLASS_CODE
         * @see KCLASS_SEEDS
         */
        @JvmStatic
        protected val KCLASS_ALL_SEEDS = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion()
            KClass${D}Companion: KClass${D}Companion(kotlin.jvm.internal.DefaultConstructorMarker)
            KClass${D}Companion: java.lang.String getFIELD()
            KClass${D}Companion: java.lang.String getSTATIC_FIELD()
            KClass${D}Companion: void getFIELD${D}annotations()
            KClass${D}Companion: void getSTATIC_FIELD${D}annotations()
            KClass${D}Companion: void staticCoMethod()
            KClass: KClass${D}Companion Companion
            KClass: KClass()
            KClass: KClass(boolean[])
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass(java.lang.String)
            KClass: KClass(java.lang.String[])
            KClass: double bar(byte,float)
            KClass: int CONST
            KClass: int bar(java.lang.String,byte,int[])
            KClass: int i
            KClass: java.lang.Short[] bazShort(java.lang.String[])
            KClass: java.lang.String FIELD
            KClass: java.lang.String JVM_FIELD
            KClass: java.lang.String STATIC_FIELD
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String fooString(java.lang.String)
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: java.lang.String s
            KClass: long l
            KClass: long[] baz(int[])
            KClass: void <clinit>()
            KClass: void foo()
            KClass: void setS(java.lang.String)
            KClass: void staticCoMethod()
        """.trimIndent()
    }

    // endregion
}
