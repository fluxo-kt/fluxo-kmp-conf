package fluxo.artifact.dsl

import fluxo.conf.dsl.FluxoKmpConfDsl
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

@FluxoKmpConfDsl
public interface ProcessorConfig : ArtifactProcessingChain {

    public val next: Provider<out ProcessorConfig?>


    /**
     * The maximum heap size for the external runned processor.
     * Only used for the unbundled external run.
     *
     * `null` by default (which means the default JRE value).
     */
    public val maxHeapSize: Property<String?>


    // region ProcessorCallType

    /**
     * The order of the call types to be used for this processor.
     *
     * [ProcessorCallType.DEFAULT_FALLBACK_ORDER] by default (`EXTERNAL`, `BUNDLED`, `IN_MEMORY`).
     *
     * @see forceBundled
     * @see forceUnbundled
     * @see forceExternal
     */
    public val callFallbackOrder: ListProperty<ProcessorCallType>

    /**
     * Whether to try using the bundled/avaliable in the classpath processor first.
     *
     * @see ProcessorCallType.IN_MEMORY
     * @see callFallbackOrder
     */
    public fun forceBundled(): Unit = force(ProcessorCallType.BUNDLED)

    /**
     * Whether to try loading the processor to memory anew
     * instead of the one bundled/avaliable in the classpath.
     *
     * @see ProcessorCallType.IN_MEMORY
     * @see callFallbackOrder
     */
    public fun forceUnbundled(): Unit = force(ProcessorCallType.IN_MEMORY)

    /**
     * Whether to try using external CLI run of the processor first.
     * This is the default behavior.
     *
     * @see ProcessorCallType.EXTERNAL
     * @see callFallbackOrder
     */
    public fun forceExternal(): Unit = force(ProcessorCallType.EXTERNAL)

    // endregion
}

private fun ProcessorConfig.force(type: ProcessorCallType) {
    val types = callFallbackOrder.get().toMutableList()
    types.remove(type)
    types.add(0, type)
    callFallbackOrder.set(types)
}
