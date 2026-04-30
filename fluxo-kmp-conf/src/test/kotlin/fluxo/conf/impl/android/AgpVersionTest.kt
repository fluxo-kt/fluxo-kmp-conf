package fluxo.conf.impl.android

import kotlin.KotlinVersion
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * RED-test seed for [AgpVersion.parseVersionString], the version-string parser used as the
 * legacy fallback (`com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION`) when AGP `>= 7.0`'s
 * `AndroidPluginVersion.getCurrent()` is unreachable.
 *
 * Falsification targets, one branch per case:
 *  - AT-boundary `KotlinVersion` numerics for the [AgpVersion.AGP_9_0] gate
 *    (8.7.x stays AGP-8 path, 9.0.0 flips to AGP-9 path);
 *  - patch-default fallback when AGP reports `MAJOR.MINOR` (no patch);
 *  - pre-release / build-metadata stripping (`-alpha`, `+meta`);
 *  - malformed-input rejection (returns `null`, never partial result).
 *
 * The reflective `detect`/`current` paths intentionally aren't covered here — they need a
 * real classloader with `com.android.build.api.AndroidPluginVersion` reachable, which the
 * plugin's unit-test classpath does NOT have (AGP is `compileOnly`). The integration suite
 * (`checks/kmp` on AGP 9, `checks/compose-desktop` on AGP 8) exercises that path live.
 */
internal class AgpVersionTest {

    @Test
    fun `parses canonical 3-part version`() {
        assertEquals(KotlinVersion(9, 1, 1), AgpVersion.parseVersionString("9.1.1"))
        assertEquals(KotlinVersion(8, 7, 2), AgpVersion.parseVersionString("8.7.2"))
    }

    @Test
    fun `defaults patch to zero when omitted`() {
        // AGP releases occasionally publish version strings without an explicit patch
        // (e.g. internal `8.8` shapes); the parser must treat them as `.0` rather than null,
        // otherwise the `>= AGP_9_0` gate goes silently false and consumers on 9.0 with
        // a 2-part version string would get the AGP-8 path.
        assertEquals(KotlinVersion(9, 0, 0), AgpVersion.parseVersionString("9.0"))
        assertEquals(KotlinVersion(8, 8, 0), AgpVersion.parseVersionString("8.8"))
    }

    @Test
    fun `strips pre-release qualifier so alphas equal stable`() {
        // `9.1.0-alpha09` and `9.1.0` MUST compare equal under `>= AGP_9_0` so an early
        // adopter on the alpha line gets the same dual-line routing as a stable user.
        assertEquals(KotlinVersion(9, 1, 0), AgpVersion.parseVersionString("9.1.0-alpha09"))
        assertEquals(KotlinVersion(9, 0, 0), AgpVersion.parseVersionString("9.0.0-rc01"))
    }

    @Test
    fun `strips build-metadata suffix`() {
        assertEquals(KotlinVersion(9, 1, 0), AgpVersion.parseVersionString("9.1.0+meta.42"))
    }

    @Test
    fun `rejects entirely non-numeric input`() {
        assertNull(AgpVersion.parseVersionString("abc"))
        assertNull(AgpVersion.parseVersionString(""))
    }

    @Test
    fun `rejects single-component input`() {
        // Major-only is ambiguous — minor is load-bearing for the `>= AGP_9_0` comparison
        // (9 vs 9.0 vs 9.1), so partial parses must hard-fail, not default minor to 0.
        assertNull(AgpVersion.parseVersionString("9"))
    }

    @Test
    fun `rejects non-numeric major or minor`() {
        assertNull(AgpVersion.parseVersionString("9.x"))
        assertNull(AgpVersion.parseVersionString("x.1"))
        assertNull(AgpVersion.parseVersionString("9.x.0"))
    }

    @Test
    fun `non-numeric patch falls through to default zero`() {
        // Per parser contract: minor MUST parse, patch MAY default. `9.1.x` has valid
        // major/minor and should yield (9, 1, 0) — same as the patch-omitted shape.
        // This documents intent so a future reader doesn't "fix" patch parsing into a
        // hard-fail and break the AGP-9 gate for unusual version strings.
        assertEquals(KotlinVersion(9, 1, 0), AgpVersion.parseVersionString("9.1.x"))
    }

    @Test
    fun `AGP_9_0 gate semantics — hostile boundary check`() {
        // Direct falsification of the `>= AGP_9_0` comparison the rest of the plugin relies
        // on. A future refactor that swaps `>=` for `>` would silently flip 9.0.0 itself
        // back to the AGP-8 path; this test catches that mutation.
        val parsed90 = AgpVersion.parseVersionString("9.0.0")
        val parsed87 = AgpVersion.parseVersionString("8.7.2")
        val parsed91 = AgpVersion.parseVersionString("9.1.1-alpha09+meta")
        assertEquals(true, parsed90!! >= AgpVersion.AGP_9_0)
        assertEquals(false, parsed87!! >= AgpVersion.AGP_9_0)
        assertEquals(true, parsed91!! >= AgpVersion.AGP_9_0)
    }
}
