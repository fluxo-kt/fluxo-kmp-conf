package fluxo.conf.impl.kotlin

/**
 * Every compiler flag the plugin adds on its own initiative, as one list.
 *
 * Emission goes only through [addDefault], so switching a default off, or gating it by the
 * consumer's Kotlin version, has exactly one place to hook in. Flags a consumer turns on through
 * their own DSL setting (explicit API, debug, release assertion removal, lambda mode, the
 * latest-settings compilation) are not defaults and stay outside this list.
 *
 * Kept free of Kotlin Gradle plugin types so it can be unit-tested (see AGENTS.md).
 */
internal enum class KotlinDefault(val flag: String) {
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
}

internal fun MutableCollection<String>.addDefault(default: KotlinDefault) {
    add(default.flag)
}
