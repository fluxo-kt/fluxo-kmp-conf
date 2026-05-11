package fluxo.conf.feat

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * RED-test seed for [isWithinDetektSupportedLangVersion] and the parser it
 * depends on.
 *
 * The previous implementation of `clampKotlinLangVersionForDetekt` compared
 * `requested.toFloat() <= max.toFloat()`. That works only while every Kotlin
 * minor is single-digit (`1.0`–`1.9`, `2.0`–`2.9`). The moment Kotlin (or a
 * consumer passing a custom value) uses `1.10` or `2.10`, the float
 * comparison flips:
 *   - `"1.10".toFloat() == 1.1f` → falsely "below 1.9".
 *   - `"2.10".toFloat() == 2.1f` → falsely "equal to 2.1" (no clamp triggered).
 *
 * The pure pair-based `parseDetektLangVersion` + lexicographic Pair compare
 * gives correct major.minor ordering. These tests fail RED against the float
 * impl on the two-digit-minor cases; GREEN against the pair impl.
 */
internal class DetektKotlinClampTest {

    @Test
    fun `parseDetektLangVersion handles single digit and two digit minors`() {
        assertEquals(KotlinVersion(1, 9), parseDetektLangVersion("1.9"))
        assertEquals(KotlinVersion(2, 0), parseDetektLangVersion("2.0"))
        assertEquals(KotlinVersion(2, 1), parseDetektLangVersion("2.1"))
        // Two-digit-minor cases — falsify the float-based comparator.
        assertEquals(KotlinVersion(1, 10), parseDetektLangVersion("1.10"))
        assertEquals(KotlinVersion(2, 10), parseDetektLangVersion("2.10"))
        // Missing minor → 0.
        assertEquals(KotlinVersion(2, 0), parseDetektLangVersion("2"))
    }

    @Test
    fun `predicate is correct for single digit Kotlin minors`() {
        // Detekt-supported max is 2.1.
        assertTrue(isWithinDetektSupportedLangVersion("1.9"))
        assertTrue(isWithinDetektSupportedLangVersion("2.0"))
        assertTrue(isWithinDetektSupportedLangVersion("2.1"))
        assertFalse(isWithinDetektSupportedLangVersion("2.2"))
        assertFalse(isWithinDetektSupportedLangVersion("3.0"))
    }

    @Test
    fun `predicate handles two digit minors that the float comparator botches`() {
        // 1.10 is a future-Kotlin shape. Float says 1.10 ≈ 1.1 < 1.9, dropping
        // it BELOW the supported max — that's wrong; semantically 1.10 > 1.9.
        // Pair-based predicate sees (1, 10) > (2, 1) is false, so 1.10 <= 2.1
        // → within range. Correct.
        assertTrue(isWithinDetektSupportedLangVersion("1.10"))

        // 2.10 is also future-Kotlin. Float says 2.10 ≈ 2.1 == max, so the
        // float predicate would NOT clamp (within range). But (2, 10) > (2, 1)
        // is true, so 2.10 > 2.1 → must be CLAMPED. Pair impl is correct.
        assertFalse(isWithinDetektSupportedLangVersion("2.10"))
    }

    @Test
    fun `parseDetektJvmTarget handles legacy and modern Gradle JVM target shapes`() {
        assertEquals(8, parseDetektJvmTarget("1.8"))
        assertEquals(17, parseDetektJvmTarget("17"))
        assertEquals(22, parseDetektJvmTarget("22"))
        assertEquals(23, parseDetektJvmTarget("23"))
        assertEquals(null, parseDetektJvmTarget("invalid"))
    }

    @Test
    fun `jvm target predicate clamps only targets beyond Detekt support`() {
        assertTrue(isWithinDetektSupportedJvmTarget("1.8"))
        assertTrue(isWithinDetektSupportedJvmTarget("17"))
        assertTrue(isWithinDetektSupportedJvmTarget("22"))
        assertFalse(isWithinDetektSupportedJvmTarget("23"))
        assertFalse(isWithinDetektSupportedJvmTarget("24"))
        assertTrue(isWithinDetektSupportedJvmTarget("invalid"))
    }
}
