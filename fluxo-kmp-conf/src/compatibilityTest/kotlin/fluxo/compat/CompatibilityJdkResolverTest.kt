package fluxo.compat

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Falsifies [resolveCompatJdkHome] / [compatJdkMajor] — the load-bearing guarantee that a
 * compatibility fixture runs on its declared JDK or fails loudly, never silently inheriting the
 * daemon JDK (the exact drift that let major-version-67 bytecode reach BCV and break v0.15.0).
 *
 * Exercises the real resolver against the real filesystem and the running JVM — no mocks: the
 * positive case points the override at this JVM's own `java.home`, which is guaranteed to be a JDK.
 */
class CompatibilityJdkResolverTest {

    @Test
    fun absentJdkFailsLoudlyInsteadOfFallingBackToDaemonJdk() {
        // 999 cannot be installed anywhere; every lookup branch must miss and the resolver MUST
        // throw rather than return the daemon JDK. A silent fallback here is the original defect.
        val error = assertThrows(IllegalStateException::class.java) {
            resolveCompatJdkHome(ABSENT_MAJOR)
        }
        assertTrue("No JDK $ABSENT_MAJOR" in error.message.orEmpty(), error.message)
    }

    @Test
    fun overridePointingAtNonJdkDirectoryIsRejected() {
        val notAJdk = File(System.getProperty("java.io.tmpdir"))
        withOverride(ABSENT_MAJOR, notAJdk.absolutePath) {
            val error = assertThrows(IllegalStateException::class.java) {
                resolveCompatJdkHome(ABSENT_MAJOR)
            }
            assertTrue("is not a JDK" in error.message.orEmpty(), error.message)
        }
    }

    @Test
    fun overrideAtRealJdkOfWrongMajorIsRejectedNotBlindlyTrusted() {
        // The override points at a genuine JDK home, but its actual feature version != requested.
        // The resolver must verify the version (release file), not trust the path — this is the
        // `java_home -v` min-version footgun that silently picked the wrong JDK.
        withOverride(ABSENT_MAJOR, System.getProperty("java.home")) {
            assertThrows(IllegalStateException::class.java) { resolveCompatJdkHome(ABSENT_MAJOR) }
        }
    }

    @Test
    fun overrideAtRealJdkOfMatchingMajorResolvesIt() {
        val thisJvmHome = File(System.getProperty("java.home"))
        withOverride(currentMajor, thisJvmHome.absolutePath) {
            assertEquals(thisJvmHome, resolveCompatJdkHome(currentMajor))
        }
    }

    @Test
    fun multiValuedJdkVersionIsNotAnExecutableRow() {
        val error = assertThrows(IllegalStateException::class.java) {
            mapOf("id" to "current-build", "jdkVersion" to "21,23").compatJdkMajor()
        }
        assertTrue("exactly one JDK" in error.message.orEmpty(), error.message)
        val singleRow = mapOf("id" to "x", "jdkVersion" to "$SINGLE_MAJOR")
        assertEquals(SINGLE_MAJOR, singleRow.compatJdkMajor())
    }

    private fun withOverride(major: Int, value: String, block: () -> Unit) {
        val key = "fluxo.compat.jdk$major"
        val previous = System.getProperty(key)
        System.setProperty(key, value)
        try {
            block()
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }

    private companion object {
        // A major version that is never installed, so lookup must fail (or hit only the override).
        const val ABSENT_MAJOR = 999

        // Any in-range JDK major: the single-valued `jdkVersion` happy path of compatJdkMajor.
        const val SINGLE_MAJOR = 17

        // The running test JVM's own feature version — guaranteed resolvable against `java.home`.
        val currentMajor: Int =
            System.getProperty("java.specification.version").removePrefix("1.").substringBefore('.')
                .toInt()
    }
}
