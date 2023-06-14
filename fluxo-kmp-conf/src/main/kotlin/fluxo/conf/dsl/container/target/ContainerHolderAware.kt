package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.InternalFluxoApi
import fluxo.conf.dsl.container.ContainerHolder

@InternalFluxoApi
public sealed interface ContainerHolderAware {
    // receiver is used to hide the holder property from the public API completion.
    @get:JvmSynthetic
    @InternalFluxoApi
    public val ContainerHolderAware.holder: ContainerHolder
}
