package fluxo.artifact.dsl

import fluxo.artifact.proc.ArtifactProcessor
import fluxo.artifact.proc.JvmShrinker
import fluxo.gradle.listProperty
import fluxo.gradle.notNullProperty
import fluxo.gradle.nullableProperty
import fluxo.log.SHOW_DEBUG_LOGS
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 *
 * @see org.jetbrains.compose.desktop.application.dsl.ProguardSettings
 */
internal class ArtifactProcessorConfigImpl(
    private val objects: ObjectFactory,
    val processor: ArtifactProcessor,
) : ProcessorConfigR8 {

    // region chaining

    override val next: Property<ProcessorConfig?> = objects.nullableProperty()

    override fun shrinkWithR8(configure: ProcessorConfigR8.() -> Unit) =
        setNextProcessor(JvmShrinker.R8, configure)

    override fun shrinkWithProGuard(configure: ProcessorConfigShrinker.() -> Unit) =
        setNextProcessor(JvmShrinker.ProGuard, configure)

    private fun setNextProcessor(
        processor: JvmShrinker,
        configure: ArtifactProcessorConfigImpl.() -> Unit,
    ) {
        val nextProcessor = next
        val value = ArtifactProcessorConfigImpl(objects, processor)
        value.configure()
        nextProcessor.set(value)
        if (SHOW_DEBUG_LOGS) {
            nextProcessor.disallowChanges()
        }
    }

    // endregion


    override val maxHeapSize: Property<String?> = objects.nullableProperty()

    override val callFallbackOrder: ListProperty<ProcessorCallType> =
        objects.listProperty<ProcessorCallType>()
            .convention(ProcessorCallType.DEFAULT_FALLBACK_ORDER)

    override val configurationFiles: ConfigurableFileCollection = objects.fileCollection()
    override val optimize: Property<Boolean> = objects.notNullProperty(true)
    override val obfuscate: Property<Boolean> = objects.notNullProperty(false)
    override val obfuscateIncrementally: Property<Boolean> = objects.notNullProperty(true)
    override val fullMode: Property<Boolean> = objects.notNullProperty(false)
}
