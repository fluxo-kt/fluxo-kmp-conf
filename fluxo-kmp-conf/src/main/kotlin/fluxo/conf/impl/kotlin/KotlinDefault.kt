package fluxo.conf.impl.kotlin

/**
 * Every compiler flag the plugin adds on its own initiative, as one list.
 *
 * Emission goes only through [addDefault], so switching a default off, or gating it by the
 * consumer's Kotlin version, has exactly one place to hook in. Flags a consumer turns on through
 * their own DSL setting (explicit API, debug, release assertion removal, lambda mode, the
 * latest-settings compilation) are not defaults and stay outside this list.
 *
 * Every flag here, and every other flag the plugin passes, must be declared by the oldest
 * supported compiler or gated on the Kotlin version that added it, use the typed compiler option
 * where one exists, and stop being passed at the language version where it becomes the default:
 * the compiler reports an unknown flag, and a flag enabling an already stable feature, as strong
 * warnings, which fail warnings-as-errors builds. A Kotlin release that removes a flag still
 * breaks such builds on older fluxo versions until a fluxo release drops it; without
 * warnings-as-errors it is one warning naming the flag.
 *
 * Kept free of Kotlin Gradle plugin types so it can be unit-tested (see AGENTS.md).
 */
internal enum class KotlinDefault(
    /** The flag passed; `null` for a default set through a typed compiler option. */
    val flag: String?,
    /**
     * The name `DISABLE_KOTLIN_DEFAULTS` takes: the flag as the build log shows it, without its
     * `-X` and value, so a consumer can copy it from there.
     */
    val switchName: String = checkNotNull(flag).removePrefix("-X").substringBefore('='),
) {
    JSR305("-Xjsr305=strict"),
    VALIDATE_BYTECODE("-Xvalidate-bytecode"),
    EMIT_JVM_TYPE_ANNOTATIONS("-Xemit-jvm-type-annotations"),

    /**
     * K2 warns on every explicit diagnostic suppression; the suppressions stay the source-level
     * contract, so the warnings about them are noise (KT-66513).
     */
    DONT_WARN_ON_ERROR_SUPPRESSION("-Xdont-warn-on-error-suppression"),

    /** Multiplatform only: expect/actual classes are still Beta and warn without it. */
    EXPECT_ACTUAL_CLASSES("-Xexpect-actual-classes"),

    /**
     * Only for a compilation whose language or API version the consumer's own Kotlin calls
     * deprecated: that warning says nothing about the code, and under warnings-as-errors a
     * Kotlin upgrade alone would fail the build. One build-end warning names the modules instead.
     */
    SUPPRESS_VERSION_WARNINGS("-Xsuppress-version-warnings"),

    /**
     * Kotlin 2.3+: report an ignored result of a function whose result must be used (Kotlin's
     * unused return value checker). Not passed below 2.3.
     */
    RETURN_VALUE_CHECKER("-Xreturn-value-checker=check"),

    /**
     * An annotation on a constructor `val`/`var` parameter also applies to the property, as
     * from language version 2.4, where it is the default and not passed.
     */
    ANNOTATION_DEFAULT_TARGET("-Xannotation-default-target=param-property"),

    /** A data class's `copy()` gets its constructor's visibility (KT-11914). */
    CONSISTENT_DATA_CLASS_COPY_VISIBILITY("-Xconsistent-data-class-copy-visibility"),

    /**
     * Type-checking `when` compiled with `invokedynamic` (`SwitchBootstraps.typeSwitch`) for JVM
     * targets 21+, on Kotlin 2.2 and 2.3; from 2.4 it is the compiler's own default there.
     */
    WHEN_EXPRESSIONS_INDY("-Xwhen-expressions=indy"),

    /** The typed `extraWarnings`, shown as `-Wextra` in the build log. */
    EXTRA_WARNINGS(flag = null, switchName = "wextra"),

    /** The typed `progressiveMode`, shown as `-progressive` in the build log. */
    PROGRESSIVE(flag = null, switchName = "progressive"),

    /** `useJdkRelease`: Kotlin's `-Xjdk-release`, javac's `--release`, Android's `noJdk`. */
    JDK_RELEASE(flag = null, switchName = "jdk-release"),
}

/** Adds [default]'s flag unless it is switched [off]; returns whether it was added. */
internal fun MutableCollection<String>.addDefault(
    default: KotlinDefault,
    off: Set<KotlinDefault>,
): Boolean = default !in off && add(checkNotNull(default.flag))

/**
 * Reads `DISABLE_KOTLIN_DEFAULTS`: names as the build log shows them, with or without `-X` and
 * a value (`-Xjsr305=strict`, `jsr305`). An unknown name fails, listing the closest valid ones:
 * Gradle silently ignores a misspelled property, so a typo would leave the default on unseen.
 */
internal fun parseDisabledKotlinDefaults(names: List<String>): Set<KotlinDefault> {
    val byName = KotlinDefault.entries.associateBy { it.switchName }
    return names.filter { it.isNotBlank() }.mapTo(LinkedHashSet()) { raw ->
        val name = raw.trim().removePrefix("-").removePrefix("X").substringBefore('=')
            .lowercase()
        byName[name] ?: throw IllegalArgumentException(
            "DISABLE_KOTLIN_DEFAULTS: unknown name '$raw'. " +
                closestNames(name, byName.keys).let {
                    if (it.isEmpty()) "" else "Did you mean ${it.joinToString(" or ")}? "
                } +
                "Valid names: ${byName.keys.joinToString()}.",
        )
    }
}

/** The [valid] names nearest to a misspelled [name], ignoring case; none when all are far off. */
internal fun closestNames(name: String, valid: Collection<String>): List<String> {
    val distances = valid.associateWith { editDistance(name.lowercase(), it.lowercase()) }
    val best = distances.values.minOrNull()
    // Up to a third of the name may differ: catches typos, not unrelated names.
    val near = best != null && best <= maxOf(1, name.length / TYPO_SHARE_DIVISOR)
    return if (near) distances.filterValues { it == best }.keys.sorted() else emptyList()
}

private const val TYPO_SHARE_DIVISOR = 3

private fun editDistance(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val cur = IntArray(b.length + 1)
        cur[0] = i + 1
        for (j in b.indices) {
            val substitution = prev[j] + if (a[i] == b[j]) 0 else 1
            cur[j + 1] = minOf(prev[j + 1] + 1, cur[j] + 1, substitution)
        }
        prev = cur
    }
    return prev[b.length]
}
