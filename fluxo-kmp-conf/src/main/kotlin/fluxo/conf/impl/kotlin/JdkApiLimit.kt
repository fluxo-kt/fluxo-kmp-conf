package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.log.logDecision
import java.io.File
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Whether a compiler can limit the JDK API it sees to the JVM target (R11, R12). Without it, code
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
) {
    val project = conf.project
    val context = conf.ctx
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
    if (limit is JdkApiLimit.Unavailable) {
        check(!(limit.failsRelease && context.isRelease)) { limit.reason }
        if (context.firstInBuild(limit.reason)) project.logger.warn("w: ${limit.reason}")
    }
    context.logDecision(
        project,
        setting = "Kotlin JDK API limit ($name)",
        value = if (limit == JdkApiLimit.Apply) target else "off",
        reason = when (limit) {
            JdkApiLimit.Apply -> "compiling with JDK $compileJdk"
            JdkApiLimit.NotNeeded -> "JDK $compileJdk has no API newer than the target"
            is JdkApiLimit.Unavailable -> limit.reason
        },
        howToChange = "useJdkRelease in fkcSetup*",
    )
    if (limit == JdkApiLimit.Apply) compilerOptions.freeCompilerArgs.add("-Xjdk-release=$jvmTarget")
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
