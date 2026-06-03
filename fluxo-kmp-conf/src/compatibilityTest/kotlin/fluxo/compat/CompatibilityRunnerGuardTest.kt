package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Architecture guard: `GradleRunner.create(` may appear ONLY in `compatRunner`
 * (CompatibilityGradleSupport.kt), the single construction point that pins each fixture's inner
 * build to its declared `jdkVersion` and is paired with `assertInnerJdk`. A raw `GradleRunner`
 * elsewhere would silently skip the JDK pin and the verification — reintroducing the daemon-JDK
 * drift that broke v0.15.0 — and a reviewer could miss it. This fails closed instead.
 */
class CompatibilityRunnerGuardTest {

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @Test
    fun onlyCompatRunnerConstructsAGradleRunner() {
        val compatDir = Path.of(System.getProperty("fluxo.repo.root"))
            .resolve("fluxo-kmp-conf/src/compatibilityTest/kotlin/fluxo/compat")
        val offenders = compatDir.walk()
            .filter { it.extension == "kt" && it.name != "CompatibilityGradleSupport.kt" }
            .filter { "GradleRunner.create(" in it.readText() }
            .map { it.name }
            .toList()
        assertEquals(
            emptyList<String>(),
            offenders,
            "Construct fixtures via compatRunner(...) (pins jdkVersion + assertInnerJdk), " +
                "not a raw GradleRunner. Offending files: $offenders",
        )
    }
}
