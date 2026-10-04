package fluxo.shrink

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class ShrinkerOutputNamesTest {

    @Test
    fun sameNameFromTwoArtifactsGetsDistinctOutputs() {
        val taken = HashSet<String>()
        val name = "runtime-saveable-desktop-1.12.1.jar"
        assertEquals(name, uniqueFileName(name, taken))
        assertEquals("runtime-saveable-desktop-1.12.1-2.jar", uniqueFileName(name, taken))
        assertEquals("runtime-saveable-desktop-1.12.1-3.jar", uniqueFileName(name, taken))
    }

    @Test
    fun caseOnlyDifferenceCountsAsClash() {
        val taken = HashSet<String>()
        uniqueFileName("Lib.jar", taken)
        assertEquals("lib-2.jar", uniqueFileName("lib.jar", taken))
    }

    @Test
    fun suffixCandidateAlreadyTakenIsSkipped() {
        val taken = HashSet<String>()
        uniqueFileName("a-2.jar", taken)
        uniqueFileName("a.jar", taken)
        assertEquals("a-3.jar", uniqueFileName("a.jar", taken))
    }

    @Test
    fun nameWithoutExtension() {
        val taken = HashSet<String>()
        uniqueFileName("LICENSE", taken)
        assertEquals("LICENSE-2", uniqueFileName("LICENSE", taken))
    }
}
