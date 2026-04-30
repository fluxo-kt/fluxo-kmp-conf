package fluxo.conf.impl.android

/**
 * Runs [block] and absorbs [NoSuchMethodError] only — every other exception propagates.
 *
 * The plugin compiles against a single catalog-pinned AGP version, but its published artefact
 * runs on whatever AGP the consumer applies. AGP routinely deprecates and removes individual
 * setters across minor versions (e.g. `targetSdk` was removed from `LibraryBaseFlavor` in AGP 9
 * — the canonical precedent; the matching `*Preview` setters carry `@Incubating` and offer no
 * stability promise). Without this guard, a setter call here causes the JVM to abort the whole
 * `setupAndroid*` flow at first runtime linkage failure, leaving the consumer's project
 * half-configured with a stack trace that points at us instead of at AGP.
 *
 * Why narrow `NoSuchMethodError` rather than `Throwable`: real bugs (NPE, ClassCast, type
 * mismatch from the consumer's `fluxoConfiguration { }` block) MUST propagate so we hear about
 * them. Only API-removal-style runtime linkage drift is the absorbed concern. Callers should
 * wrap individual setter calls (or single-purpose helpers like
 * `applyAgpSdkProperty` / `applyCompileSdk` / `applyMinSdk`) so that one removed property does
 * not also skip a sibling property's assignment.
 *
 * Inlined so the catch frame folds into the caller; zero allocation, zero stack frame on
 * the GREEN path.
 */
@Suppress("SwallowedException")
internal inline fun noSuchMethodSafe(block: () -> Unit) {
    try {
        block()
    } catch (_: NoSuchMethodError) {
        // AGP version drift — the targeted setter has been removed/renamed in the consumer's
        // applied AGP version. Skipping is safer than aborting; consumers can override directly
        // via the AGP DSL if our version-aware path is no longer applicable.
    }
}
