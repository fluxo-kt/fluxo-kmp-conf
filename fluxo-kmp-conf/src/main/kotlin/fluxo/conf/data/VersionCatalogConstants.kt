package fluxo.conf.data

/**
 * Version-catalog alias names this plugin opportunistically recognises in the
 * consumer's `gradle/libs.versions.toml`. If the consumer defines any of these
 * aliases, the wrapped feature wires it in automatically; absence is silent.
 *
 * Stays `internal` deliberately — consumers consume these as TOML alias
 * strings, not Kotlin imports. The canonical names are the string values
 * below; the README documents them for consumer reference.
 */
internal object VersionCatalogConstants {

    /**
     * Compose Tooling library — when present, excluded from
     * dependency-analysis reports (it's a debug-only dep).
     */
    const val VC_ANDROIDX_COMPOSE_TOOLING_ALIAS = "androidx.compose.ui.tooling"

    /** LeakCanary — excluded from dependency-analysis (debug-only). */
    const val VC_SQUARE_LEAK_CANARY_ALIAS = "square.leakcanary"

    /** Plumber — excluded from dependency-analysis (debug-only). */
    const val VC_SQUARE_PLUMBER_ALIAS = "square.plumber"

    /**
     * Bundle name for deps the plugin should pin against accidental upstream
     * downgrade. Consumers list dependencies in this bundle; the plugin
     * applies version constraints accordingly.
     */
    const val VC_PINNED_BUNDLE_ALIAS = "pinned"
}
