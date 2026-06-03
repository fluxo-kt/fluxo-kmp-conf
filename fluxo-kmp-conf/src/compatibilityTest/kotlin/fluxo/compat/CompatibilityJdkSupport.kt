package fluxo.compat

import java.io.File
import java.nio.file.Path

/**
 * Per-fixture JDK pinning for the compatibility TestKit harness.
 *
 * Every fixture must run on the JDK its `compat/matrix.tsv` `jdkVersion` declares, independent of
 * the ambient daemon JDK. Without that, an unpinned consumer `jvmTarget` defaults to the daemon JDK
 * (`KotlinConfigSetup` `JRE_VERSION`), so `compileXxx` emits bytecode whose major version
 * tracks the daemon; a bytecode-reading task (BCV `androidApiBuild`, ASM-based) then throws
 * `Unsupported class file major version N` once the daemon outruns the wrapped tool's ASM ceiling
 * (BCV 0.18.1 ≤ Java 22 / major-66). That asymmetry — every other axis (Gradle/KGP/AGP) enforced,
 * only `jdkVersion` dead metadata — passed dev CI (JDK 21) yet failed release (JDK 23) on the same
 * commit, sinking v0.15.0's first tag.
 */

/**
 * Pins the inner build's daemon JVM to JDK [major] via the `org.gradle.java.home` Gradle property.
 * `GradleRunner` exposes no `withJavaHome`, and the Tooling API daemon it drives does not
 * honour the `JAVA_HOME` env var — `org.gradle.java.home` (forward-slashed for Windows
 * `.properties` safety) is the sole reliable selector. Appended (not overwritten) so a fixture's
 * own `gradle.properties` (e.g. Compose's `android.useAndroidX`) is preserved; idempotent because
 * a fixture may build twice on one dir (dependency-guard seed, then the run).
 */
internal fun pinInnerJdk(projectDir: Path, major: Int) {
    val home = resolveCompatJdkHome(major).absolutePath.replace('\\', '/')
    val line = "org.gradle.java.home=$home"
    val file = projectDir.resolve("gradle.properties").toFile()
    if (!file.exists() || line !in file.readText()) file.appendText("\n$line\n")
}

/**
 * Single-valued `jdkVersion` for executed rows; the only multi-valued row (`current-build` =
 * "21,23") is informational — referenced by no `selectedRows(...)` and never run via TestKit — so a
 * comma here is a wiring mistake, not a supported case.
 */
internal fun Map<String, String>.compatJdkMajor(): Int =
    getValue("jdkVersion").toIntOrNull() ?: error(
        "Row '${getValue("id")}' declares jdkVersion='${getValue("jdkVersion")}'; an executed " +
            "compatibility fixture must declare exactly one JDK major.",
    )

/**
 * Resolves a JDK home whose *actual* feature version is exactly [major]; every candidate is
 * verified via [jdkFeatureVersion], nothing trusted by path/name. Lookup is CI-first, then local
 * dev, and **fails loudly rather than falling back to the daemon JDK** — a silent fallback is
 * exactly the defect this guards against:
 *  1. `-Dfluxo.compat.jdk<major>=<home>` explicit override (still version-verified).
 *  2. `JAVA_HOME_<major>_<arch>` (GitHub `actions/setup-java` multi-version convention).
 *  3. installed JDKs under sdkman + the macOS `JavaVirtualMachines` roots.
 *
 * `/usr/libexec/java_home -v N` is deliberately NOT used — its `-v` is *minimum* version, so it
 * returns the highest JDK ≥ N (e.g. 21 for `-v 17`), silently picking the wrong major.
 */
@Suppress("ReturnCount") // first-hit-wins lookup reads clearest as sequential early returns.
internal fun resolveCompatJdkHome(major: Int): File {
    System.getProperty("fluxo.compat.jdk$major")?.let { override ->
        return File(override).takeIfMajor(major)
            ?: error("fluxo.compat.jdk$major='$override' is not a JDK $major home.")
    }
    System.getenv().entries
        .firstOrNull { isJavaHomeEnvKey(it.key, major) }
        ?.value?.let(::File)?.takeIfMajor(major)?.let { return it }
    candidateJdkHomes().firstOrNull { it.jdkFeatureVersion() == major }?.let { return it }
    error(
        "No JDK $major found for a compatibility fixture (compat/matrix.tsv jdkVersion=$major). " +
            "Set -Dfluxo.compat.jdk$major=<home>, export JAVA_HOME_${major}_<arch>, " +
            "or install JDK $major (sdkman / /usr/lib/jvm / JavaVirtualMachines).",
    )
}

/**
 * Whether [key] is the `actions/setup-java` env var that names a JDK of feature [major]:
 * `JAVA_HOME_<major>_<arch>` (e.g. `JAVA_HOME_17_X64`, `JAVA_HOME_17_ARM64`). The trailing `_` is
 * load-bearing — without it `JAVA_HOME_170_*` would match `major=17` and `JAVA_HOME_17_*` would
 * match `major=1`. A literal prefix, never a regex (this is fixed syntax, not a pattern language).
 */
internal fun isJavaHomeEnvKey(key: String, major: Int): Boolean =
    key.startsWith("JAVA_HOME_${major}_")

/**
 * Installed-JDK homes to scan, covering every dev platform: sdkman candidates (home = entry),
 * Linux distro JDKs (`/usr/lib/jvm/<name>`), and macOS bundles (entry/`Contents/Home`). Each entry
 * is emitted both as-is and with a `Contents/Home` suffix; the non-existent variant is filtered
 * downstream by [jdkFeatureVersion]'s `bin/java` + `release` checks.
 */
private fun candidateJdkHomes(): Sequence<File> {
    val userHome = System.getProperty("user.home")
    return sequenceOf(
        "$userHome/.sdkman/candidates/java",
        "/usr/lib/jvm",
        "/Library/Java/JavaVirtualMachines",
        "$userHome/Library/Java/JavaVirtualMachines",
    ).flatMap { File(it).listFiles()?.asSequence() ?: emptySequence() }
        .flatMap { sequenceOf(it, File(it, "Contents/Home")) }
}

private fun File.takeIfMajor(major: Int): File? = takeIf { it.jdkFeatureVersion() == major }

/**
 * Feature (major) version read OFFLINE from the JDK's `release` file
 * (`JAVA_VERSION="17.0.x"` → `17`, `"1.8.0"` → `8`); `null` if this is not a JDK home or the
 * version is unreadable. Offline so resolution never spawns a process, and exact so
 * `java_home`-style min-version drift can't slip in.
 */
private fun File.jdkFeatureVersion(): Int? {
    val hasJava = File(this, "bin/java").exists() || File(this, "bin/java.exe").exists()
    val release = File(this, "release")
    if (!hasJava || !release.exists()) return null
    return release.readLines().firstOrNull { it.startsWith("JAVA_VERSION=") }
        ?.substringAfter('=')?.trim('"', ' ')
        ?.removePrefix("1.")?.substringBefore('.')?.toIntOrNull()
}
