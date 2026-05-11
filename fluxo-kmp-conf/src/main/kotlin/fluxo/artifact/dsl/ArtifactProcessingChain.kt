package fluxo.artifact.dsl

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.impl.EMPTY_FUN

@FluxoKmpConfDsl
public interface ArtifactProcessingChain {

    /**
     * Reserved DSL slot for artifact shadowing.
     *
     * Shadowing is intentionally compile-time disabled until the processor has
     * reproducible-archive, publication, and shrinker-chain semantics matching
     * the existing R8/ProGuard processors.
     */
    @Deprecated("Artifact shadowing is not supported yet.", level = DeprecationLevel.ERROR)
    public fun shadow(): Unit =
        throw UnsupportedOperationException("Artifact shadowing is not supported yet.")


    public fun shrinkWithR8(configure: ProcessorConfigR8.() -> Unit = EMPTY_FUN)

    public fun shrinkWithProGuard(configure: ProcessorConfigShrinker.() -> Unit = EMPTY_FUN)
}
