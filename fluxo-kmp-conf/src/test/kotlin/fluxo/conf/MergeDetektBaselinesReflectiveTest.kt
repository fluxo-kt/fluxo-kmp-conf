package fluxo.conf

import io.github.detekt.tooling.api.BaselineProvider
import org.junit.jupiter.api.Test

/**
 * RED-test seed for the reflective baseline-loader fallback in
 * [MergeDetektBaselinesTask] (line ~80).
 *
 * Production code path is:
 *   1. Try `BaselineProvider.load()` — Detekt's stable API.
 *   2. On failure, reflectively `Class.forName(...).getDeclaredConstructor().newInstance() as BaselineProvider`
 *      against `io.gitlab.arturbosch.detekt.core.baseline.BaselineFormat`.
 *
 * If a Detekt bump renames or removes either side, the consumer-side merge
 * task fails at runtime with a cryptic `ClassNotFoundException` /
 * `NoSuchMethodException` / `ClassCastException`. These tests fail RED at
 * our build time instead, pointing the maintainer at the exact reflective
 * target that needs updating.
 *
 * Tests run on the test classpath which already pulls `detekt-core`
 * transitively via the plugin's `implementation` configuration — so the
 * RED tests run against the same Detekt classes the production code does.
 */
internal class MergeDetektBaselinesReflectiveTest {

    private val cl = javaClass.classLoader

    @Test
    fun `BaselineProvider load is callable from Kotlin`() {
        // MergeDetektBaselinesTask.kt:77 — primary path.
        // Production uses `BaselineProvider.load()` (Kotlin call syntax;
        // signature is `Companion.load(ClassLoader = ...)` with a default).
        // Calling it directly mirrors the production path and falsifies the
        // entire chain — companion existence + load method + default arg + the
        // ServiceLoader plumbing detekt uses internally.
        val provider = BaselineProvider.load()
        check(provider != null) {
            "BaselineProvider.load() returned null — ServiceLoader chain broken; " +
                "fallback at MergeDetektBaselinesTask.kt:80 will be hit on every " +
                "consumer build (incurring reflection cost) instead of the cheap path."
        }
    }

    @Test
    fun `detekt BaselineFormat class is reflectively reachable`() {
        // MergeDetektBaselinesTask.kt:80 — fallback path target FQN.
        val clazz = Class.forName(
            "io.gitlab.arturbosch.detekt.core.baseline.BaselineFormat",
            true,
            cl,
        )
        // MergeDetektBaselinesTask.kt:81 — fallback uses no-arg ctor + newInstance.
        val ctor = clazz.getDeclaredConstructor()
        check(BaselineProvider::class.java.isAssignableFrom(clazz)) {
            "BaselineFormat is no longer a BaselineProvider — the `as BaselineProvider` " +
                "cast at MergeDetektBaselinesTask.kt:81 will throw ClassCastException."
        }
        // Spot-instantiate to also catch ctor-removal regressions; instances are
        // cheap and have no side effects.
        ctor.newInstance()
    }
}
