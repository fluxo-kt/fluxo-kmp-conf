package fluxo.conf.impl.android

import org.gradle.api.Project

/**
 * Snapshot of the Android Gradle Plugin version currently visible on a project's classpath.
 *
 * Exposed as a `KotlinVersion` for cheap, allocation-free comparison; the project rule against
 * `String.toFloat()` for version comparison applies (`"1.10".toFloat() == 1.1f`). Patch numbers
 * collapse into the [KotlinVersion.patch] slot, so `8.7.2` becomes `KotlinVersion(8, 7, 2)` and
 * `9.1.0-alpha09` becomes `KotlinVersion(9, 1, 0)` — pre-release qualifiers are dropped.
 *
 * `null` means AGP is not on the classpath at all (not "version unknown"). Production code MUST
 * branch on `null` separately from `>= AGP_9_0`, since plenty of consumer modules don't pull AGP
 * in at all (pure-JVM, KMP without `androidTarget()`, etc.).
 */
internal object AgpVersion {

    /**
     * AGP `9.0.0`, the boundary where:
     *  - `com.android.library` + `kotlin("multiplatform")` co-application is hard-rejected.
     *  - The KMP+Android integration moves to `com.android.kotlin.multiplatform.library`.
     *  - `CommonExtension` collapses its 6 type parameters.
     *  - `defaultConfig`/`lint` block-form helpers are removed in favour of property `apply { }`.
     *  - Built-in Kotlin (`android.builtInKotlin=true`) is the default.
     *  - Min Gradle is 9.1, min JDK is 17, min build-tools is 36.0.0.
     */
    @Suppress("MagicNumber")
    val AGP_9_0: KotlinVersion = KotlinVersion(major = 9, minor = 0, patch = 0)

    /**
     * Reads AGP version from the [project]'s buildscript classloader. Returns `null` if AGP is
     * not on the classpath OR if reflective access fails.
     *
     * Not cached: it runs about twice per Android KMP module, a few reflective lookups each,
     * while a static per-class-loader cache would keep every build's buildscript class loader
     * alive for the daemon's lifetime. (A `ConcurrentHashMap` cache also can't hold the `null`
     * of a build without AGP: `getOrPut` then throws a bare NullPointerException.)
     */
    fun current(project: Project): KotlinVersion? = detect(project.buildscript.classLoader)

    /**
     * Convenience guard: `true` when AGP `>= 9.0` is on the classpath. `false` when AGP < 9.0
     * OR AGP is absent — both routes through the legacy `com.android.library` configuration
     * path, which is correct: AGP 8.x users keep the old DSL; AGP-absent users don't hit the
     * Android setup at all.
     */
    fun isAgp9OrLater(project: Project): Boolean {
        val v = current(project) ?: return false
        return v >= AGP_9_0
    }

    /**
     * Probe the classloader for the canonical AGP version source.
     *
     * AGP `>= 7.0` ships [com.android.build.api.AndroidPluginVersion] with a public
     * `getCurrent()` static helper backed by
     * `CurrentAndroidGradlePluginVersion.CURRENT_AGP_VERSION`.
     * The companion-object access (`AndroidPluginVersion.Companion.getCurrent()`) survives the
     * 8.x → 9.x migration unchanged and requires only AGP on the classpath; it does NOT require
     * AGP to be applied as a plugin. It covers the whole supported AGP range (8.4+).
     * Returns `null` if it is absent.
     */
    private fun detect(cl: ClassLoader): KotlinVersion? = detectViaAndroidPluginVersion(cl)

    /**
     * Primary detection path: `com.android.build.api.AndroidPluginVersion.getCurrent()` — AGP 7+.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun detectViaAndroidPluginVersion(cl: ClassLoader): KotlinVersion? = try {
        val klass = Class.forName("com.android.build.api.AndroidPluginVersion", false, cl)
        val companion = klass.getField("Companion").get(null)
        val current = companion.javaClass.getMethod("getCurrent").invoke(companion)
        val major = current.javaClass.getMethod("getMajor").invoke(current) as Int
        val minor = current.javaClass.getMethod("getMinor").invoke(current) as Int
        val micro = current.javaClass.getMethod("getMicro").invoke(current) as Int
        KotlinVersion(major, minor, micro)
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: LinkageError) {
        null
    }
}
