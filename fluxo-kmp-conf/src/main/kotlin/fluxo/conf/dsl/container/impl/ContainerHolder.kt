package fluxo.conf.dsl.container.impl

import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.d
import org.gradle.api.NamedDomainObjectSet

internal class ContainerHolder(
    configuration: FluxoConfigurationExtensionImpl,
    private val onlyTarget: KmpTargetCode?,
) : ContainerContext(configuration) {

    private val KmpTargetCode.isEnabled: Boolean
        get() = context.isTargetEnabled(this)

    val containers: NamedDomainObjectSet<Container> =
        objects.namedDomainObjectSet(Container::class.java)

    fun <T : KmpTargetContainerImpl<*>> configure(
        targetName: String,
        contruct: (ContainerContext, targetName: String) -> T,
        code: KmpTargetCode,
        action: T.() -> Unit,
    ) {
        if (onlyTarget != null && onlyTarget != code) {
            // Return early if configuring only a single target.
            // It's not an error as default KMP targets can be applied to JVM-obly subprojects.
            // So just skip the unexpected ones.
            project.logger.d("Skipping target '$targetName' because only '$onlyTarget' is allowed.")
            return
        }

        var container = findByName<T>(targetName)
        if (container == null) {
            // Don't contruct the container for turned-off targets.
            if (!code.isEnabled) {
                return
            }
            container = contruct(this, targetName)
            require(containers.add(container)) { "Couldn't add container for target '$targetName'" }
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
