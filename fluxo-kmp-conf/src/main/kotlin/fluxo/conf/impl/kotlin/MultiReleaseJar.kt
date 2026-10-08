package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.log.logDecision
import fluxo.log.w
import java.io.File
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

/**
 * Multi-release jar for a KMP JVM target: code in `src/<target><N>Main` (e.g. `src/jvm11Main`)
 * goes to `META-INF/versions/N` of the target's jar, which a Java N+ runtime loads in place of the
 * base classes. That is how a library with an old JVM floor ships code using newer JDK API
 * without reflection.
 *
 * The directory alone turns it on: no setting can then disagree with the sources. Kotlin code
 * there is a KGP compilation named `<N>Main` (KGP names its source set `<target><N>Main`),
 * compiled against main's code and dependencies; [versionedJvmTarget] gives it
 * JVM target N and so the JDK API limit N. KGP compiles Java code there too, with `--release N`;
 * a `module-info.java` gets the main classes patched into its module, as javac requires every
 * package a module exports to exist in it.
 *
 * Only directories are read, once per JVM target at configuration; plain Kotlin/JVM modules
 * are not covered (their single source-set layout has no `<target>` prefix to key on).
 */
internal fun KotlinTarget.setupMultiReleaseJar(conf: FluxoConfigurationExtensionImpl) {
    if (platformType != KotlinPlatformType.jvm) return
    val pattern = Regex(Regex.escape(targetName) + "(\\d+)Main")
    val versions = project.file("src").list().orEmpty()
        .mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1)?.toInt() }
        .sorted()
    val (ignored, used) = versions.partition { it < FIRST_MULTI_RELEASE_JDK }
    if (ignored.isNotEmpty()) {
        project.logger.w(
            "src/$targetName${ignored.first()}Main is not compiled: Java reads " +
                "META-INF/versions only from $FIRST_MULTI_RELEASE_JDK on. Move that code to " +
                "${targetName}Main, or to ${targetName}${FIRST_MULTI_RELEASE_JDK}Main or higher.",
        )
    }
    if (used.isEmpty()) return
    conf.ctx.logDecision(
        project,
        setting = "Multi-release jar ($targetName)",
        value = used.joinToString(),
        reason = "src/$targetName<N>Main directories exist",
        howToChange = "add or remove those directories",
    )
    val main = compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
    val jar = project.tasks.named(artifactsTaskName, Jar::class.java)
    jar.configure { manifest.attributes(mapOf("Multi-Release" to true)) }
    // The JVM applies `versions/` only inside a jar, so tests on main's class directories would
    // never run the versioned code. On the jar, the test JVM picks the variant for its own
    // version, so each TEST_JDK leg covers the code that version ships.
    val testTaskName = "${targetName}Test"
    project.tasks.withType(Test::class.java).configureEach {
        if (name == testTaskName) {
            val original = classpath
            classpath = project.files(jar) + original.minus(main.output.classesDirs)
        }
    }
    for (version in used) {
        val compilation = compilations.create("$version$MAIN_SUFFIX")
        // Not `associateWith(main)`: KGP then compiles against main's jar, which holds this
        // compilation's output, a task cycle. Main's classes as friend paths keep its `internal`
        // declarations visible, as a versioned class usually replaces an internal one.
        compilation.compileDependencyFiles +=
            main.output.classesDirs + main.compileDependencyFiles
        compilation.compileTaskProvider.configure {
            (this as BaseKotlinCompile).friendPaths.from(main.output.classesDirs)
        }
        val javaDir = project.file("src/${compilation.defaultSourceSet.name}/java")
        javaModuleName(javaDir.resolve("module-info.java"))?.let { module ->
            val patched = compilation.output.classesDirs + main.output.classesDirs
            compilation.javaCompileTaskProviderCompat?.configure {
                options.compilerArgumentProviders.add(
                    CommandLineArgumentProvider {
                        listOf("--patch-module", "$module=${patched.asPath}")
                    },
                )
            }
        }
        jar.configure {
            into("$VERSIONS_DIR/$version") { from(compilation.output.allOutputs) }
        }
    }
}

/**
 * The JVM target of a compilation [setupMultiReleaseJar] created: the version it ships under,
 * whatever the module's target. Null for every other compilation.
 */
internal fun KotlinCompilation<*>.versionedJvmTarget(): String? {
    if (platformType != KotlinPlatformType.jvm) return null
    return name.removeSuffix(MAIN_SUFFIX).takeIf { it != name }?.toIntOrNull()
        ?.takeIf { it >= FIRST_MULTI_RELEASE_JDK }?.toString()
}

/**
 * The module name declared in a `module-info.java`, or null without one. Comments are removed
 * first, so a commented-out declaration can't match; the declaration itself is
 * `[open] module <name> {`.
 */
private fun javaModuleName(file: File): String? {
    if (!file.isFile) return null
    val code = file.readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("//[^\n]*"), " ")
    return Regex("\\bmodule\\s+([\\w.]+)\\s*\\{").find(code)?.groupValues?.get(1)
}

private const val MAIN_SUFFIX = "Main"

private const val VERSIONS_DIR = "META-INF/versions"

/** Java 9 introduced multi-release jars (JEP 238); older runtimes ignore `versions/`. */
private const val FIRST_MULTI_RELEASE_JDK = 9
