package fluxo.conf.feat

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Version strings must parse to comparable `major.minor` pairs: as floats, `"2.10"` equals `2.1`
 * and `"1.10"` ranks below `1.9`. Comparisons against the Detekt limits are plain `<=` on the
 * parsed values, so only the parsers are tested.
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
    fun `parseDetektJvmTarget handles legacy and modern Gradle JVM target shapes`() {
        assertEquals(8, parseDetektJvmTarget("1.8"))
        assertEquals(17, parseDetektJvmTarget("17"))
        assertEquals(22, parseDetektJvmTarget("22"))
        assertEquals(23, parseDetektJvmTarget("23"))
        assertEquals(null, parseDetektJvmTarget("invalid"))
    }
}
