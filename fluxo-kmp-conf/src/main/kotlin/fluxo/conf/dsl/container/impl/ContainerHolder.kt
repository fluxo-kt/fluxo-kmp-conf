package fluxo.conf.dsl.container.impl

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.target.KmpTargetCode
import org.gradle.api.Project

internal class ContainerHolder(
    context: FluxoKmpConfContext,
    project: Project,
) : ContainerContext(context, project) {

    private val allKmpTargetsEnabled: Boolean = context.allKmpTargetsEnabled
    private val requestedKmpTargets: Set<KmpTargetCode> = context.requestedKmpTargets

    val containers = objects.namedDomainObjectSet(ContainerImpl::class.java)

    fun add(container: ContainerImpl): Boolean {
        return if (container !is KotlinTargetContainerImpl<*>
            || allKmpTargetsEnabled
            || container.code in requestedKmpTargets
        ) {
            containers.add(container)
            true
        } else {
            false
        }
    }

    fun <T : ContainerImpl> configure(
        targetName: String,
        contruct: (ContainerContext, targetName: String) -> T,
        action: T.() -> Unit,
    ) {
        var container = findByName<T>(targetName)
        if (container == null) {
            // FIXME: Avoid contructing the container for disabled targets
            container = contruct(this, targetName)
            if (!add(container)) {
                // Avoid calling the action for disabled targets
                return
            }
        }
        action(container)
    }

    fun <T, C : CustomTypeContainer<T>> configureCustom(
        name: String,
        contruct: (ContainerContext, name: String) -> C,
        action: T.() -> Unit,
    ) {
        var container = findByName<C>(name)
        if (container == null) {
            container = contruct(this, name)
            check(containers.add(container)) { "Couldn't add container for name '$name'" }
        }
        container.add(action)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : ContainerImpl> findByName(name: String) = containers.findByName(name) as T?
}
