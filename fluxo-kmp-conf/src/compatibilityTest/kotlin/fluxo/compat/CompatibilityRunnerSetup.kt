package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult

/**
 * Tripwires applied to every fixture run, so each row fails on what a strict consumer would hit:
 * - Gradle deprecations caused by the plugin under test, checked by [assertNoOwnDeprecations].
 *   Not `--warning-mode=fail`: that also fails on deprecations inside AGP, Compose or Detekt,
 *   which no change here can clear and which would keep every Android row red.
 * - `--configuration-cache` + `problems=fail`: a configuration-cache violation (this repo's own
 *   builds use `problems=warn`, which lets such violations pass silently).
 * - `android.debug.obsoleteApi`: AGP prints the caller of every obsolete API; the output check
 *   in [FORBIDDEN_OUTPUT_SIGNATURES] turns that print into a failure.
 * - The init script turns on the plugin's own `allWarningsAsErrors`. The plugin applies it only
 *   on CI or release builds, hence [TRIPWIRE_ENVIRONMENT]. Going through the consumer switch keeps
 *   the plugin's deliberate exclusions (tests, metadata, JS), so a fixture fails exactly where a
 *   strict consumer would, e.g. on a compiler flag that the consumer's Kotlin no longer knows.
 *
 * The same script applies the row's `kotlinLangVersion`/`kotlinApiVersion` columns through the
 * plugin's root DSL, which every module inherits; `-` keeps the consumer's KGP default. Set
 * centrally because each fixture writes its own build scripts, and a column a fixture forgets to
 * read would claim coverage that never ran.
 */
internal fun tripwireArguments(projectDir: Path, row: Map<String, String>): List<String> {
    val initScript = projectDir.resolve("fluxo-compat-tripwires.init.gradle.kts")
    val versionSetters = listOf("kotlinLangVersion", "kotlinApiVersion")
        .filter { row.getValue(it) != "-" }
        .joinToString("\n                ") { column ->
            val setter = "set" + column.replaceFirstChar(Char::uppercaseChar)
            val value = row.getValue(column)
            """conf.javaClass.getMethod("$setter", String::class.java).invoke(conf, "$value")"""
        }
    // The plugin under test may come only from this tree's local repository. The working version
    // can equal a released one, and a fixture's own `buildscript { repositories }` resolves the
    // plugin's module from there, not from `pluginManagement`: the Kotlin/JVM fixture silently
    // ran the Plugin Portal's 0.15.1 that way. Every other repository excludes these coordinates,
    // so a missing local copy fails as "could not find" instead of resolving a release. Content
    // filters, not `exclusiveContent`: Gradle rejects that in `pluginManagement` as soon as a
    // project declares `buildscript` repositories, a consumer shape the fixtures must keep.
    initScript.writeText(
        """
        val fluxoLocalRepo = java.io.File("${localMavenRepoPath()}").toURI().toString().trimEnd('/')
        fun RepositoryHandler.fluxoOnlyFromLocal() = all {
            if (this is MavenArtifactRepository && url.toString().trimEnd('/') != fluxoLocalRepo) {
                content {
                    excludeModule("io.github.fluxo-kt", "fluxo-kmp-conf")
                    excludeGroup("${pluginId()}")
                }
            }
        }
        beforeSettings { pluginManagement.repositories.fluxoOnlyFromLocal() }
        allprojects {
            buildscript.repositories {
                maven(fluxoLocalRepo)
                fluxoOnlyFromLocal()
            }
        }
        rootProject {
            pluginManager.withPlugin("${pluginId()}") {
                val conf = extensions.getByName("fluxoConfiguration")
                conf.javaClass.getMethod("setAllWarningsAsErrors", java.lang.Boolean::class.java)
                    .invoke(conf, true)
                $versionSetters
            }
        }
        """.trimIndent(),
    )
    return listOf(
        "--warning-mode=all",
        "-Dorg.gradle.deprecation.trace=true",
        "--configuration-cache",
        "--configuration-cache-problems=fail",
        "-Pandroid.debug.obsoleteApi=true",
        "--init-script",
        initScript.toString(),
    )
}

internal val TRIPWIRE_ENVIRONMENT = mapOf("CI" to "true")

/**
 * Fails when a Gradle deprecation printed by the build was triggered by our code.
 *
 * With `org.gradle.deprecation.trace` each deprecation message is followed by its stack trace.
 * The caller that matters is the first frame outside Gradle and the language runtimes: it is
 * the code that used the deprecated API. Ours means the plugin (`fluxo.*`, the top-level `Fkc`
 * facade) or the fixture's own scripts. Deprecations owned by AGP, Compose, Detekt and other
 * third parties are printed for the record, never failed: the fix is theirs.
 */
internal fun BuildResult.assertNoOwnDeprecations() {
    val lines = output.lines()
    val deprecations = lines.withIndex()
        .filter { (_, line) -> line.isGradleDeprecation() }
        .mapNotNull { (i, line) -> lines.callerAfter(i)?.let { "${line.trim()}\n    caller: $it" } }
    val (own, foreign) = deprecations.partition { entry ->
        OWN_FRAME_PREFIXES.any { "caller: $it" in entry }
    }
    if (foreign.isNotEmpty()) {
        val report = foreign.toSortedSet().joinToString("\n")
        println("Third-party Gradle deprecations (not failing):\n$report")
    }
    check(own.isEmpty()) { "Gradle deprecations caused by our code:\n" + own.joinToString("\n") }
}

/** First stack frame after line [index] that is outside Gradle and the language runtimes. */
private fun List<String>.callerAfter(index: Int): String? =
    asSequence().drop(index + 1)
        .takeWhile { it.trimStart().startsWith("at ") }
        // `at app//fluxo.X.y(F.kt:1)` → `fluxo.X.y`: the loader prefix ends at the last slash.
        .map { it.trimStart().removePrefix("at ").substringBefore('(').substringAfterLast('/') }
        .firstOrNull { frame -> RUNTIME_FRAME_PREFIXES.none(frame::startsWith) }

private fun String.isGradleDeprecation(): Boolean =
    "has been deprecated" in this || "This is scheduled to be removed in Gradle" in this ||
        "This will fail with an error in Gradle" in this

private val RUNTIME_FRAME_PREFIXES = listOf(
    "org.gradle.", "worker.org.gradle.", "java.", "jdk.", "sun.", "com.sun.",
    "kotlin.", "groovy.", "org.codehaus.groovy.",
)

private val OWN_FRAME_PREFIXES = listOf("fluxo.", "Fkc", "Build_gradle", "Settings_gradle")

/**
 * One TestKit Gradle user home shared by every fixture and kept between runs.
 *
 * A fresh home per row re-downloaded the whole toolchain (about half a gigabyte per row) and
 * started a cold daemon each time, which was most of the suite's wall time, and the temp dirs
 * were never deleted. Sharing is safe: Gradle's caches are versioned and lock across processes,
 * and fixtures write nothing into the home. Reused daemons are what consumers run on too, so
 * state leaking between builds in one daemon is a real defect to surface, not test noise.
 */
internal fun compatGradleUserHome(): Path {
    val dir = checkNotNull(System.getProperty("fluxo.compat.gradle.home")) {
        "fluxo.compat.gradle.home system property is missing"
    }
    return Path.of(dir)
}

/**
 * A fresh directory for one test's fixture projects, under the build dir the `compatibilityTest`
 * task deletes before each run and after a green one (see the task's comment for why there and
 * not JUnit's `@TempDir`).
 */
internal fun newCompatProjectsDir(): Path {
    val root = Path.of(
        checkNotNull(System.getProperty("fluxo.compat.projects.dir")) {
            "fluxo.compat.projects.dir system property is missing"
        },
    )
    return Files.createTempDirectory(Files.createDirectories(root), "case")
}
