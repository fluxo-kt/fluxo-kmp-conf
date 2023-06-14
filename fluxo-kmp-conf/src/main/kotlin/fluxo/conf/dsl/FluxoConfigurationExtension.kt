package fluxo.conf.dsl

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.container.ContainerHolder
import fluxo.conf.dsl.container.target.KmpConfigurationContainerDsl
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Project

@FluxoKmpConfDsl
public abstract class FluxoConfigurationExtension
@Inject internal constructor(
    private val context: FluxoKmpConfContext,
    private val configureContainers: (Collection<Container>) -> Unit,
) {
    @get:Inject
    internal abstract val project: Project

    private val isConfigured = AtomicBoolean(false)

    public fun configure(action: KmpConfigurationContainerDsl.() -> Unit) {
        if (!isConfigured.compareAndSet(false, true)) {
            throw GradleException("$NAME.configure can only be invoked once")
        }

        val holder = ContainerHolder(context, project)
        action(KmpConfigurationContainerDsl(holder))
        configureContainers(holder.containers.sortedBy { it.sortOrder })
    }

    internal companion object {
        internal const val NAME = "fluxoConfiguration"
    }
}
