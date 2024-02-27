package fluxo.conf.dsl.impl

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.FluxoConfigurationExtension
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory

internal interface FluxoConfigurationExtensionImplBase {
    val project: Project
    val objects: ObjectFactory
    val ctx: FluxoKmpConfContext
    val parent: FluxoConfigurationExtension?
}
