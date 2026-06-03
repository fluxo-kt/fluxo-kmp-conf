package fluxo.conf.impl.kotlin

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Falsifies the Apple-simulator probe's pure logic without `xcrun` or a Gradle project.
 *
 * The headline regression: the probe matched the pretty-printed `"platform" : "iOS"` substring. If
 * `simctl` ever emits compact JSON the match silently fails and every simulator test is skipped — a
 * false green. The compact-JSON case is the RED reproducer: it breaks the old spacing-coupled match
 * and passes only after whitespace normalization.
 */
internal class AppleSimulatorProbeTest {

    // Real `xcrun simctl list runtimes available --json` shape (NSJSONSerialization pretty-print).
    private val prettyIosJson = """
        {
          "runtimes" : [
            {
              "platform" : "iOS",
              "identifier" : "com.apple.CoreSimulator.SimRuntime.iOS-17-0",
              "version" : "17.0",
              "isAvailable" : true,
              "name" : "iOS 17.0"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `pretty JSON reports the listed runtime`() {
        assertTrue(simctlReportsRuntime(prettyIosJson, "iOS"))
        assertFalse(simctlReportsRuntime(prettyIosJson, "tvOS"))
    }

    @Test
    fun `compact JSON reports the runtime (the regression)`() {
        // The exact regression: old `contains("\"platform\" : \"iOS\"")` fails here;
        // whitespace normalization fixes it.
        val compact =
            """{"runtimes":[{"platform":"iOS","isAvailable":true,"name":"iOS 17.0"}]}"""
        assertTrue(simctlReportsRuntime(compact, "iOS"))
    }

    @Test
    fun `empty runtime list reports nothing`() {
        assertFalse(simctlReportsRuntime("""{"runtimes":[]}""", "iOS"))
        assertFalse(simctlReportsRuntime("", "iOS"))
    }

    @Test
    fun `only the present platform is reported`() {
        val tvOnly = """{"runtimes":[{"platform":"tvOS","name":"tvOS 17.0"}]}"""
        assertTrue(simctlReportsRuntime(tvOnly, "tvOS"))
        assertFalse(simctlReportsRuntime(tvOnly, "iOS"))
    }

    @Test
    fun `closing quote anchors the match (no partial hit)`() {
        // A value of "iOS-17-0" must NOT satisfy a query for "iOS".
        assertFalse(simctlReportsRuntime("""{"runtimes":[{"platform":"iOS-17-0"}]}""", "iOS"))
    }

    @Test
    fun `simulator targets map to their platform`() {
        assertEquals("iOS", appleSimulatorPlatform("iosSimulatorArm64"))
        assertEquals("iOS", appleSimulatorPlatform("iosX64"))
        assertEquals("tvOS", appleSimulatorPlatform("tvosSimulatorArm64"))
        assertEquals("tvOS", appleSimulatorPlatform("tvosX64"))
        assertEquals("watchOS", appleSimulatorPlatform("watchosSimulatorArm64"))
        assertEquals("watchOS", appleSimulatorPlatform("watchosX64"))
    }

    @Test
    fun `host and device targets need no simulator probe`() {
        // Device target (real hardware), host targets — must skip the probe (null), not mis-map.
        assertNull(appleSimulatorPlatform("iosArm64"))
        assertNull(appleSimulatorPlatform("macosArm64"))
        assertNull(appleSimulatorPlatform("linuxX64"))
        assertNull(appleSimulatorPlatform("mingwX64"))
    }

    @Test
    fun `simulator test task shapes are recognized`() {
        val t = "iosSimulatorArm64"
        val id = "IosSimulatorArm64"
        assertTrue(isSimulatorTestTask("${t}Test", t, id))
        assertTrue(isSimulatorTestTask("${t}BackgroundTest", t, id))
        assertTrue(isSimulatorTestTask("compileTestKotlin$id", t, id))
        assertTrue(isSimulatorTestTask("linkDebugTest$id", t, id))
        assertTrue(isSimulatorTestTask("linkReleaseTest$id", t, id))
    }

    @Test
    fun `non-test and non-simulator tasks are not gated`() {
        val t = "iosSimulatorArm64"
        val id = "IosSimulatorArm64"
        // `&&` binds tighter than `||`: a `link` task without the `Test<id>` suffix isn't gated.
        assertFalse(isSimulatorTestTask("linkDebugFramework$id", t, id))
        assertFalse(isSimulatorTestTask("compileKotlin$id", t, id))
        assertFalse(isSimulatorTestTask("${t}MainKlibrary", t, id))
        assertFalse(isSimulatorTestTask("jvmTest", t, id))
    }
}
