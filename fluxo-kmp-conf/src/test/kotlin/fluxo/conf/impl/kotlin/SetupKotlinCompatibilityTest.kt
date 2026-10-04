package fluxo.conf.impl.kotlin

import kotlin.KotlinVersion
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for the KGP-free helpers in `KotlinVersionTable.kt` (KGP types are `compileOnly`,
 * so they are absent from this classpath). The version-string parser is the fallback when KGP
 * renames its plugin-version method; the stdlib skew guard turns a catalog mismatch into a
 * configuration error instead of a CI-only warning-as-error. The JVM target limit is read from
 * the consumer's KGP and is exercised by the compat suite on real KGP versions.
 */
internal class SetupKotlinCompatibilityTest {

    // region parseKotlinPluginVersion — pre-release suffix tolerance

    @Test
    fun `parses stable plugin version strings`() {
        assertEquals(KotlinVersion(2, 0, 21), parseKotlinPluginVersion("2.0.21"))
        assertEquals(KotlinVersion(1, 9, 0), parseKotlinPluginVersion("1.9.0"))
        assertEquals(KotlinVersion(2, 1, 0), parseKotlinPluginVersion("2.1"))
    }

    @Test
    fun `parses pre-release suffixed plugin version strings`() {
        // KGP exposes RC/Beta/dev qualifiers via dash; we strip them.
        assertEquals(KotlinVersion(2, 1, 0), parseKotlinPluginVersion("2.1.0-RC2"))
        assertEquals(KotlinVersion(2, 3, 0), parseKotlinPluginVersion("2.3.0-Beta3"))
        assertEquals(KotlinVersion(2, 0, 21), parseKotlinPluginVersion("2.0.21-stable"))
    }

    // endregion

    // region kotlinStdlibSkewError — directional stdlib>compiler fail-fast guard

    @Test
    fun `stdlib strictly newer than compiler is rejected, equal or older accepted`() {
        val compiler = KotlinVersion(2, 3, 20)
        // The exact production footgun: a patch-newer stdlib than the compiler
        // (kotlin=2.3.20 vs kotlinCoreLibraries=2.3.21). Mutating the guard's `<=`
        // to `<` (rejecting equality) flips the equal/older asserts RED; dropping
        // the comparison entirely flips the not-null asserts RED.
        assertNotNull(
            kotlinStdlibSkewError(compiler, "2.3.21"),
            "patch-newer stdlib must be rejected",
        )
        assertNotNull(
            kotlinStdlibSkewError(compiler, "2.4.0"),
            "minor-newer stdlib must be rejected",
        )
        assertNotNull(
            kotlinStdlibSkewError(compiler, "3.0.0"),
            "major-newer stdlib must be rejected",
        )
        assertNull(kotlinStdlibSkewError(compiler, "2.3.20"), "equal stdlib is compatible")
        assertNull(kotlinStdlibSkewError(compiler, "2.3.19"), "older patch stdlib is compatible")
        assertNull(kotlinStdlibSkewError(compiler, "2.2.0"), "older minor stdlib is compatible")
    }

    @Test
    fun `skew check tolerates pre-release, missing patch, and absent or unparseable versions`() {
        val compiler = KotlinVersion(2, 3, 20)
        // Pre-release suffix stripped before compare: 2.3.21-RC2 > 2.3.20 → rejected.
        assertNotNull(kotlinStdlibSkewError(compiler, "2.3.21-RC2"))
        // Missing patch parsed as .0: 2.4 > 2.3.20 → rejected.
        assertNotNull(kotlinStdlibSkewError(compiler, "2.4"))
        // Equal with pre-release suffix: 2.3.20 == 2.3.20 → compatible.
        assertNull(kotlinStdlibSkewError(compiler, "2.3.20-Beta1"))
        // Absent / blank / unparseable → skip (null), never crash the build.
        assertNull(kotlinStdlibSkewError(compiler, null))
        assertNull(kotlinStdlibSkewError(compiler, "   "))
        assertNull(kotlinStdlibSkewError(compiler, "latest"))
    }

    // endregion
}
