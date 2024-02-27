package fluxo.artifact.dsl

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.impl.EMPTY_FUN

@FluxoKmpConfDsl
public interface ArtifactProcessingChain {

    public fun shadow(): Unit = TODO("Not yet implemented")


    public fun shrinkWithR8(configure: ProcessorConfigR8.() -> Unit = EMPTY_FUN)

    public fun shrinkWithProGuard(configure: ProcessorConfigShrinker.() -> Unit = EMPTY_FUN)
}
