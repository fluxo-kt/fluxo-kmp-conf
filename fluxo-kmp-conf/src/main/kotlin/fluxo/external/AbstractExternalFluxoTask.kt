package fluxo.external

import fluxo.gradle.notNullProperty
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.process.ExecOperations

internal abstract class AbstractExternalFluxoTask : DefaultTask() {

    @get:Inject
    protected abstract val objects: ObjectFactory

    @get:Inject
    protected abstract val providers: ProviderFactory

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @get:Inject
    protected abstract val fileOperations: FileSystemOperations

    @get:LocalState
    @Suppress("LeakingThis")
    protected val logsDir: DirectoryProperty = objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("logs/fluxo/$name"))

    @get:Internal
    @Suppress("LeakingThis")
    val verbose: Property<Boolean> = objects.notNullProperty<Boolean>().apply {
        set(providers.provider { logger.isDebugEnabled })
    }

    @get:Internal
    internal val runExternalTool: ExternalToolRunner
        get() = ExternalToolRunner(verbose, logsDir, execOperations)
}
