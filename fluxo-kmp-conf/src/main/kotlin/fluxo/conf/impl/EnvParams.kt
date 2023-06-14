package fluxo.conf.impl

internal object EnvParams {

    // FIXME: Implement or remove it
    val metadataOnly: Boolean get() = System.getProperty("metadata_only") != null

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use KMP_TARGETS instead")
    val splitTargets: Boolean get() = System.getProperty("split_targets") != null
}
