package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.envOrPropValue
import fluxo.log.logDecision
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

/**
 * `TEST_JDK=<N>` (env var or Gradle property) runs every JVM `Test` task of the module on JDK N.
 *
 * Why: a library compiled for an old JVM (e.g. 8) but built on a new JDK has code paths chosen
 * at runtime by JDK version; only a test run on each supported JDK proves them. The build stays
 * on one JDK and compiles once; CI runs one leg per `TEST_JDK`. Tests compile to the module's
 * JVM target, so any JDK at or above it can load them.
 *
 * Through Gradle toolchains, so N is found among installed JDKs (or provisioned where the
 * consumer enabled that). A convention: a launcher the consumer sets on a task wins. Android
 * unit tests are `Test` tasks and follow it too.
 */
internal fun Project.setupTestJdk(ctx: FluxoKmpConfContext) {
    val value = envOrPropValue(TEST_JDK) ?: return
    // A typo must not silently test on the build JDK, which is the run it was meant to replace.
    val jdk = requireNotNull(value.toIntOrNull()?.takeIf { it > 0 }) {
        "$TEST_JDK=$value is not a JDK feature version; use a number such as 17 or 21."
    }
    ctx.logDecision(
        this,
        setting = "Test JDK",
        value = jdk,
        reason = "$TEST_JDK is set",
        howToChange = "unset $TEST_JDK",
    )
    // Looked up per task: `Test` tasks exist only once `java-base` (which adds the toolchain
    // service) is applied, by the Kotlin plugin or AGP, possibly after this call.
    tasks.withType(Test::class.java).configureEach {
        val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
        javaLauncher.convention(
            toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(jdk)) },
        )
    }
}

private const val TEST_JDK = "TEST_JDK"
