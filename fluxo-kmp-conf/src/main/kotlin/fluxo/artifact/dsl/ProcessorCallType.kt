package fluxo.artifact.dsl

public enum class ProcessorCallType {
    BUNDLED,
    IN_MEMORY,
    EXTERNAL,
    ;

    internal companion object {
        @JvmField
        internal val DEFAULT_FALLBACK_ORDER =
            arrayOf(EXTERNAL, BUNDLED, IN_MEMORY).asList()

        @JvmField
        internal val PREFER_BUNDLED_FALLBACK_ORDER =
            arrayOf(BUNDLED, EXTERNAL, IN_MEMORY).asList()
    }
}
