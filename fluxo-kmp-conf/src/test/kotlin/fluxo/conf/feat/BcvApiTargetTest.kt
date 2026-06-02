package fluxo.conf.feat

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * RED-test seed for [getTargetForTaskName].
 *
 * The previous implementation ended `else -> error("Unsupported API check task name: …")`. BCV
 * creates compare tasks beyond the modelled android/jvm/js — the `apiCheck` umbrella and
 * `klibApiCheck` (once KLib validation is on) — and the per-target filter runs `withType` over all
 * of them whenever JVM or ANDROID is disabled. So `error(…)` crashed configuration for real KMP
 * consumers. These cases pin the open-world contract: known targets map, everything else is `null`,
 * nothing throws. The `klibApiCheck` case guards the exact regression.
 */
internal class BcvApiTargetTest {

    @Test
    fun `modelled targets map to their ApiTarget`() {
        assertEquals(ApiTarget.ANDROID, getTargetForTaskName("androidApiCheck"))
        assertEquals(ApiTarget.JVM, getTargetForTaskName("jvmApiCheck"))
        assertEquals(ApiTarget.JS, getTargetForTaskName("jsApiCheck"))
    }

    @Test
    fun `klibApiCheck is skipped, never throws (the regression)`() {
        assertNull(getTargetForTaskName("klibApiCheck"))
    }

    @Test
    fun `unmodelled and future per-target compare tasks are skipped`() {
        // Open-world: a target BCV may add later must degrade to skip, not crash.
        assertNull(getTargetForTaskName("wasmApiCheck"))
        assertNull(getTargetForTaskName("nativeApiCheck"))
        assertNull(getTargetForTaskName("linuxX64ApiCheck"))
    }

    @Test
    fun `the apiCheck umbrella and non-suffixed names are skipped`() {
        // `apiCheck` lacks the `ApiCheck` suffix, so it's left to the umbrella, not toggled.
        assertNull(getTargetForTaskName("apiCheck"))
        assertNull(getTargetForTaskName("check"))
        assertNull(getTargetForTaskName(""))
    }

    @Test
    fun `suffix match is case-sensitive and exact`() {
        // Only the lowercase BCV task names match; near-misses must skip rather than mis-map.
        assertNull(getTargetForTaskName("JvmApiCheck"))
        assertNull(getTargetForTaskName("jvmapicheck"))
        assertNull(getTargetForTaskName("jvmApiCheckExtra"))
    }
}
