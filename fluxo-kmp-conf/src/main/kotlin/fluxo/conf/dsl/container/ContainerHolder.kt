package fluxo.conf.dsl.container

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.InternalFluxoApi
import fluxo.conf.dsl.container.target.KmpTarget
import fluxo.conf.target.KmpTargetCode
import fluxo.conf.target.KmpTargetCode.Companion.property
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project

@InternalFluxoApi
public class ContainerHolder
internal constructor(
    context: FluxoKmpConfContext,
    project: Project,
) : ContainerContext(context, project) {

    private val allKmpTargetsEnabled: Boolean = context.allKmpTargetsEnabled
    private val requestedKmpTargets: Set<KmpTargetCode> = context.requestedKmpTargets

    internal val containers: NamedDomainObjectSet<Container> =
        objects.namedDomainObjectSet(Container::class.java)


    internal fun add(container: Container): Boolean {
        return if (container !is KmpTarget<*>
            || allKmpTargetsEnabled
            || container.property() in requestedKmpTargets
        ) {
            containers.add(container)
        } else {
            false
        }
    }

    internal fun <T : Container> configure(
        targetName: String,
        contruct: (ContainerContext, targetName: String) -> T,
        action: T.() -> Unit,
    ) {
        val container = findByName(targetName) ?: contruct(this, targetName)
        action(container)
        add(container)
    }


    internal fun findKotlinContainer(): KotlinExtensionActionContainer? =
        findByName(KotlinExtensionActionContainer.NAME)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Container> findByName(name: String) = containers.findByName(name) as T?
}
