package fluxo.artifact.proc

import fluxo.artifact.dsl.ProcessorConfig
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

@Suppress("LongParameterList")
internal class ProcessorSetup<out P : ArtifactProcessor, out C : ProcessorConfig>(
    val conf: FluxoConfigurationExtensionImpl,
    val processor: P,
    val config: C,
    val chainId: Int = 0,
    val stepId: Int = 0,
    val dependencies: List<Any>? = null,
    val runAfter: List<Any>? = null,
    val chainState: ProcessorChainState? = null,
    val chainForLog: String? = null,
    val processAsApp: Boolean = false,
)

internal class ProcessorChainState(
    val mainJar: Provider<RegularFile>,
    val inputFiles: FileCollection?,
    val mappingFile: Provider<RegularFile>? = null,
)
