package fluxo.conf.dsl.container

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.InternalFluxoApi
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory

@InternalFluxoApi
public abstract class ContainerContext
internal constructor(
    internal val context: FluxoKmpConfContext,
    project: Project,
) {
    internal val kotlinPluginVersion: KotlinVersion get() = context.kotlinPluginVersion

    internal val objects: ObjectFactory = project.objects
}
