package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * CI runs this suite as one leg per fixture class, each selected with `--tests` in
 * `.github/workflows/build.yml`. A test class no leg's pattern matches would never run on CI
 * while every leg stays green, so each class must match at least one pattern.
 */
class CompatibilityShardGuardTest {

    @Test
    fun everyCompatTestClassRunsInSomeCiLeg() {
        val root = Path.of(System.getProperty("fluxo.repo.root"))
        val workflow = root.resolve(".github/workflows/build.yml").readText()
        val patterns = TESTS_ARG.findAll(workflow).map { it.groupValues[1] }.toList()
        check(patterns.isNotEmpty()) { "No --tests patterns found in build.yml" }
        // Gradle's `--tests` wildcard `*` matches any characters.
        val regexes = patterns.map { p ->
            Regex(p.split('*').joinToString(".*", transform = Regex::escape))
        }

        val unrun = root.resolve("fluxo-kmp-conf/src/compatibilityTest/kotlin/fluxo/compat")
            .listDirectoryEntries("*Test.kt")
            .map { "fluxo.compat." + it.name.removeSuffix(".kt") }
            .filter { fqn -> regexes.none { it.matches(fqn) } }
        assertEquals(emptyList<String>(), unrun, "No CI leg in build.yml runs: $unrun")
    }

    private companion object {
        val TESTS_ARG = Regex("""--tests '([^']+)'""")
    }
}
