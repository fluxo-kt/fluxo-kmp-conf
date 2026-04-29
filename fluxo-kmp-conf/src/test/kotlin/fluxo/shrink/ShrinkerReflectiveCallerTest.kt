package fluxo.shrink

import java.util.Properties
import org.junit.jupiter.api.Test

/**
 * RED-test seed for the reflection surface used by [ShrinkerReflectiveCaller].
 *
 * Each test falsifies one of the `Class.forName` / `getDeclaredConstructor` /
 * `getDeclaredMethod` / `getField` calls in `ShrinkerReflectiveCaller.kt`.
 * If a ProGuard or R8 upgrade renames, moves, or removes any of these
 * symbols, the corresponding test FAILS — pointing at the exact reflective
 * target so the bump can't slip into production with a silent runtime
 * `NoSuchMethodException` / `NoSuchFieldException` / `ClassNotFoundException`.
 *
 * The classes are loaded from the same testImplementation classpath
 * (`libs.proguard.plugin`, `libs.proguard.core`, `libs.r8`) the plugin
 * provisions at runtime, so a broken upstream contract is observable here
 * before any consumer ever invokes the shrinker chain.
 */
internal class ShrinkerReflectiveCallerTest {

    private val cl = javaClass.classLoader

    // region ProGuard

    @Test
    fun `proguard ProGuard class has the constructor and methods reflective caller invokes`() {
        val clazz = Class.forName("proguard.ProGuard", true, cl)
        val confClass = Class.forName("proguard.Configuration", true, cl)
        // ShrinkerReflectiveCaller.kt:137 — `clazz.getDeclaredConstructor(confClass)`
        assertReachable("ProGuard(Configuration)") {
            clazz.getDeclaredConstructor(confClass)
        }
        // ShrinkerReflectiveCaller.kt:141 — `.getDeclaredMethod("execute")`
        assertReachable("ProGuard.execute()") {
            clazz.getDeclaredMethod("execute")
        }
        // ShrinkerReflectiveCaller.kt:180 — `.getDeclaredMethod("getVersion")`
        assertReachable("ProGuard.getVersion()") {
            clazz.getDeclaredMethod("getVersion")
        }
    }

    @Test
    fun `proguard Configuration has the no-arg constructor reflective caller invokes`() {
        // ShrinkerReflectiveCaller.kt:123
        val confClass = Class.forName("proguard.Configuration", true, cl)
        assertReachable("proguard.Configuration()") {
            confClass.getDeclaredConstructor()
        }
    }

    @Test
    fun `proguard ConfigurationParser has the constructor and parse method reflective caller invokes`() {
        // ShrinkerReflectiveCaller.kt:125-129, 133
        val parserClass = Class.forName("proguard.ConfigurationParser", true, cl)
        val confClass = Class.forName("proguard.Configuration", true, cl)
        assertReachable("ConfigurationParser(String[], Properties)") {
            parserClass.getDeclaredConstructor(
                Array<String>::class.java,
                Properties::class.java,
            )
        }
        assertReachable("ConfigurationParser.parse(Configuration)") {
            parserClass.getDeclaredMethod("parse", confClass)
        }
    }

    // endregion

    // region R8

    @Test
    fun `r8 R8 has the main(String[]) entry point reflective caller invokes`() {
        // ShrinkerReflectiveCaller.kt:147
        val clazz = Class.forName("com.android.tools.r8.R8", true, cl)
        assertReachable("R8.main(String[])") {
            clazz.getDeclaredMethod("main", Array<String>::class.java)
        }
    }

    @Test
    fun `r8 Version exposes either LABEL field or getVersionString method`() {
        // ShrinkerReflectiveCaller.kt:188-194 falls back from LABEL to getVersionString;
        // both paths going dark is the regression we need to catch.
        val versionClass = Class.forName("com.android.tools.r8.Version", true, cl)
        val hasLabel = runCatching { versionClass.getField("LABEL") }.isSuccess
        val hasGetVersion = runCatching {
            versionClass.getDeclaredMethod("getVersionString")
        }.isSuccess
        check(hasLabel || hasGetVersion) {
            "com.android.tools.r8.Version exposes neither LABEL nor " +
                "getVersionString() — `ShrinkerReflectiveCaller.v(R8)` will fail at " +
                "runtime. Audit AGP/R8 release notes; update " +
                "`ShrinkerReflectiveCaller.kt:185-194` accordingly."
        }
    }

    // endregion

    private inline fun assertReachable(label: String, block: () -> Unit) {
        val result = runCatching(block)
        check(result.isSuccess) {
            "Reflective target unreachable: $label\n" +
                "  Caller: ShrinkerReflectiveCaller.kt — bump audit needed.\n" +
                "  Cause: ${result.exceptionOrNull()}"
        }
    }
}
