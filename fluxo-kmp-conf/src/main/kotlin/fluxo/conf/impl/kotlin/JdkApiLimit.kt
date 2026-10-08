package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.log.FluxoProblem
import fluxo.log.logDecision
import fluxo.log.reportProblem
import fluxo.log.w
import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Whether a compiler can limit the JDK API it sees to the JVM target. Without it, code
 * compiled for 17 on JDK 21 links JDK 21 methods and then fails on a Java 17 runtime with
 * `NoSuchMethodError`. javac (`--release`) and Kotlin (`-Xjdk-release`) both read the API of each
 * release from the compile JDK's `lib/ct.sym`, so the decision is shared and each compiler adds
 * only its own gates.
 */
internal sealed interface JdkApiLimit {
    data object Apply : JdkApiLimit

    /** The compile JDK's API is no newer than the target, so there is nothing to hide. */
    data object NotNeeded : JdkApiLimit

    /** Needed but impossible; [reason] names the cause and the fix. */
    class Unavailable(val reason: String, val failsRelease: Boolean) : JdkApiLimit
}

/**
 * A missing `ct.sym` (a trimmed or jlinked JDK) is a hard compiler error, so it is detected
 * here and the limit skipped with a warning rather than failing a default-on setting; a release
 * build fails instead, since it would ship bytecode never checked against the target's API.
 */
internal fun jdkApiLimit(target: Int, compileJdk: Int, compileJdkHome: File): JdkApiLimit = when {
    compileJdk <= target -> JdkApiLimit.NotNeeded

    !compileJdkHome.resolve("lib/ct.sym").isFile -> JdkApiLimit.Unavailable(
        "JDK $compileJdk at $compileJdkHome has no lib/ct.sym, so the JDK API can't be limited " +
            "to JVM $target. Build with a full JDK, or set useJdkRelease = false to accept it.",
        failsRelease = true,
    )

    else -> JdkApiLimit.Apply
}

/**
 * Adds `-Xjdk-release` to this Kotlin JVM compile task when [jdkApiLimit] allows it.
 *
 * Decided from the JDK this task compiles with (its Kotlin toolchain), not the JDK running Gradle:
 * that comparison missed every toolchain build, so a JDK 21 toolchain on a JDK 17 daemon compiled
 * JDK 21 calls into JVM 17 bytecode. Decided when the task is configured, after the build scripts
 * ran, so a toolchain set after `fkcSetup*()` counts. Not in a lazy provider: the configuration
 * cache serialises such a lambda with everything it captures and runs it at execution, where the
 * plugin's context is not available.
 */
internal fun KotlinJvmCompile.limitKotlinJdkApi(
    conf: FluxoConfigurationExtensionImpl,
    jvmTarget: String,
    inheritedArgs: Provider<List<String>>?,
) {
    val project = conf.project
    val target = jvmTarget.toJvmMajorVersion()
    val compileJdk = kotlinJavaToolchain.javaVersion.get().majorVersion.toInt()
    var limit = jdkApiLimit(target, compileJdk, compileJdkHome(project))
    // JDK 18-22 `ct.sym` gives Kotlin wrong JDK 18+ API data (JDK-8331027, KT-67668), fixed in
    // JDK 23 only, so Kotlin can't limit to 18-22 on an older compile JDK. javac is not affected.
    if (limit == JdkApiLimit.Apply && target in JRE_17 + 1 until JRE_23 && compileJdk < JRE_23) {
        limit = JdkApiLimit.Unavailable(
            "Kotlin can't limit the JDK API to JVM $target when compiling with JDK $compileJdk " +
                "(JDK-8331027, fixed in JDK 23). Compile with JDK 23+, or target 17.",
            failsRelease = false,
        )
    }
    conf.report(limit, "Kotlin JDK API limit ($name)", target, compileJdk)
    if (limit == JdkApiLimit.Apply) {
        compilerOptions.addArgsUnlessInherited(listOf("-Xjdk-release=$jvmTarget"), inheritedArgs)
        // The classpath is resolved only when the task runs, so it is checked there.
        val classpath = libraries
        val isRelease = conf.ctx.isRelease
        val gradleCache = project.gradle.gradleUserHomeDir
        doFirst { checkNoJdkClassesOnClasspath(classpath, gradleCache, target, isRelease) }
        // An up-to-date or cached task skips doFirst, and CI compiles release and non-release
        // builds alike, so a release build would reuse output a non-release build only warned
        // about. Keying the task on release mode makes a release build compile, and check.
        inputs.property("fluxoReleaseBuild", isRelease)
    }
}

/**
 * kotlinc resolves `java.*` from a classpath jar as well as from the `-Xjdk-release` API, so a
 * jar declaring JDK classes lifts the limit: `android.jar` declares `InputStream.readAllBytes`,
 * which then compiles at JVM 8 and fails on Java 8 with `NoSuchMethodError`. javac's `--release`
 * ignores such jars. Such a build looks limited while it isn't, so it warns, and fails a release
 * build, which would ship that bytecode.
 *
 * Runs on every compile, so it opens only jars outside [gradleCache]: repository libraries all
 * resolve into it and don't ship `java.*`, while the jars that do (`android.jar` from the SDK, a
 * local `files(…)` stub) live elsewhere. That keeps the cost at a few file stats.
 */
private fun Task.checkNoJdkClassesOnClasspath(
    classpath: FileCollection,
    gradleCache: File,
    target: Int,
    isRelease: Boolean,
) {
    val offending = classpath.filter {
        when {
            it.startsWith(gradleCache) -> false

            it.isDirectory -> it.resolve("java").isDirectory

            it.isFile && it.extension == "jar" -> ZipFile(it).use { zip ->
                zip.entries().asSequence().any { e -> e.name.startsWith("java/") }
            }

            else -> false
        }
    }.files
    if (offending.isEmpty()) return
    val message = "$path: ${offending.joinToString { it.name }} on the classpath declares " +
        "java.* classes, so the JDK API limit to JVM $target does not hold for Kotlin: newer " +
        "JDK methods it declares compile and then fail at runtime on Java $target. " +
        "Take it off this compilation's classpath (reach Android API by reflection or from an " +
        "Android-only source set), or set useJdkRelease = false to accept the risk."
    check(!isRelease) { message }
    logger.w(message)
}

/**
 * Android code runs against the device's API, described by `android.jar` on the classpath, and
 * the JDK's own API is not on a device. KGP hides the JDK (`noJdk`) only for AGP 8's
 * `kotlin-android` plugin, so on AGP 9 and in KMP Android targets a call to JDK-only API compiled
 * and then failed at runtime. `-Xjdk-release` is no substitute: it still shows the JDK API up to
 * the target, and fails outright where KGP already hides the JDK ("JDK_HOME path is not
 * specified"). Java sources need nothing: AGP gives javac the Android SDK's own core library
 * image, never the JDK's.
 */
internal fun KotlinJvmCompile.hideJdkFromAndroidCode(conf: FluxoConfigurationExtensionImpl) {
    // KGP sets and locks it on the `kotlin-android` path; nothing to change there.
    if (compilerOptions.noJdk.orNull != true) compilerOptions.noJdk.convention(true)
    conf.ctx.logDecision(
        conf.project,
        setting = "Kotlin JDK API ($name)",
        value = "android.jar only",
        reason = "Android code runs against the device's API, not the JDK's",
        howToChange = "useJdkRelease in fkcSetup*",
    )
}

/**
 * Sets javac's `--release` on this task when [jdkApiLimit] allows it. The compile JDK comes
 * from the task's own `javaCompiler`, which is exact with or without a toolchain.
 *
 * javac rejects `--add-exports`, `--add-reads` and `--patch-module` together with `--release`,
 * and consumers add those in their own `tasks.withType<JavaCompile>()` blocks, which run after
 * this one. So that gate is a provider over the task's live argument list, read when Gradle
 * resolves `release`; a module passing them compiles unlimited, as before.
 */
internal fun JavaCompile.limitJavaJdkApi(
    conf: FluxoConfigurationExtensionImpl,
    jvmTarget: String,
) {
    val target = jvmTarget.toJvmMajorVersion()
    val metadata = javaCompiler.get().metadata
    val compileJdk = metadata.languageVersion.asInt()
    val limit = jdkApiLimit(target, compileJdk, metadata.installationPath.asFile)
    conf.report(limit, "javac JDK API limit ($name)", target, compileJdk)
    if (limit != JdkApiLimit.Apply) return
    val args = options.compilerArgs
    options.release.set(
        conf.project.providers.provider {
            target.takeUnless { args.any { it in JAVAC_ARGS_REJECTED_WITH_RELEASE } }
        },
    )
}

private val JAVAC_ARGS_REJECTED_WITH_RELEASE =
    setOf("--add-exports", "--add-reads", "--patch-module")

/** One log line per task; an unavailable limit warns once per build and fails a release build. */
private fun FluxoConfigurationExtensionImpl.report(
    limit: JdkApiLimit,
    setting: String,
    target: Int,
    compileJdk: Int,
) {
    if (limit is JdkApiLimit.Unavailable) {
        check(!(limit.failsRelease && ctx.isRelease)) { limit.reason }
        if (ctx.firstInBuild(limit.reason)) {
            project.reportProblem(FluxoProblem.JDK_API_NOT_LIMITED, limit.reason)
        }
    }
    ctx.logDecision(
        project,
        setting = setting,
        value = if (limit == JdkApiLimit.Apply) target else "off",
        reason = when (limit) {
            JdkApiLimit.Apply -> "compiling with JDK $compileJdk"
            JdkApiLimit.NotNeeded -> "JDK $compileJdk has no API newer than the target"
            is JdkApiLimit.Unavailable -> limit.reason
        },
        howToChange = "useJdkRelease in fkcSetup*",
    )
}

/**
 * The home of the JDK compiling this project, for the `ct.sym` probe. KGP exposes only the
 * toolchain's version, so the home is resolved the way KGP wires it by default: the project's
 * Java toolchain when one is set, else the JDK running Gradle. A consumer pointing a single task
 * at another JDK with `kotlinJavaToolchain.jdk.use(...)` is not seen here.
 */
private fun compileJdkHome(project: Project): File {
    val spec = project.extensions.findByType(JavaPluginExtension::class.java)?.toolchain
    if (spec != null && spec.languageVersion.isPresent) {
        val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
        return toolchains.compilerFor(spec).get().metadata.installationPath.asFile
    }
    return File(System.getProperty("java.home"))
}
