package fluxo.conf.dsl.impl

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.KmpConfigurationContainerDsl as KmpConfDsl
import fluxo.conf.dsl.container.impl.ContainerHolder
import fluxo.conf.dsl.container.impl.ContainerImpl
import fluxo.conf.dsl.container.impl.KmpConfigurationContainerDslImpl
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType.KOTLIN_MULTIPLATFORM
import fluxo.conf.impl.findByType
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

internal typealias ConfigureContainers =
    (ConfigurationType, FluxoConfigurationExtensionImpl, Collection<ContainerImpl>) -> Unit

@FluxoKmpConfDsl
internal abstract class FluxoConfigurationExtensionImpl
@Inject internal constructor(
    override val context: FluxoKmpConfContext,
    private val configureContainers: ConfigureContainers,
) : FluxoConfigurationExtension,
    FluxoConfigurationExtensionKotlinImpl,
    FluxoConfigurationExtensionAndroidImpl,
    FluxoConfigurationExtensionPublicationImpl {

    @Volatile
    private var parentCache: FluxoConfigurationExtension? = null


    @get:Inject
    abstract override val project: Project

    override val parent: FluxoConfigurationExtension?
        get() {
            return parentCache
                ?: project.parent?.extensions?.findByType<FluxoConfigurationExtension>()
                    ?.also { parentCache = it }
        }

    @get:Input
    protected abstract val hasConfigurationAction: Property<Boolean?>

    @get:Input
    protected abstract val defaultConfiguration: Property<(KmpConfDsl.() -> Unit)?>


    @get:Input
    protected abstract val skipDefaultConfigurationsProp: Property<Boolean?>
    override var skipDefaultConfigurations: Boolean
        get() = skipDefaultConfigurationsProp.orNull == true
        set(value) = skipDefaultConfigurationsProp.set(value)


    override fun defaultConfiguration(action: KmpConfDsl.() -> Unit) {
        defaultConfiguration.set(action)
    }


    override fun configure(action: KmpConfDsl.() -> Unit) {
        if (hasConfigurationAction.orNull == true) {
            throw GradleException("$NAME.configure can only be invoked once")
        }
        hasConfigurationAction.set(true)

        val holder = ContainerHolder(context, project)
        val dsl = KmpConfigurationContainerDslImpl(holder)
        applyDefaultKmpConfigurations(dsl)
        action(dsl)
        configureContainers(KOTLIN_MULTIPLATFORM, this, holder.containers.sorted())
    }

    private fun applyDefaultKmpConfigurations(dsl: KmpConfigurationContainerDslImpl) {
        // Collect all defaults
        val defaults = mutableListOf<FluxoConfigurationExtension>()
        var p: Project? = project
        var ext: FluxoConfigurationExtension? = this
        while (p != null) {
            if (ext != null) {
                if (ext.skipDefaultConfigurations) {
                    break
                }
                defaults.add(ext)
            }
            p = p.parent ?: break
            ext = p.extensions.findByType<FluxoConfigurationExtension>()
        }

        // Apply defaults
        defaults.asReversed().forEach {
            (it as? FluxoConfigurationExtensionImpl)
                ?.defaultConfiguration?.orNull?.invoke(dsl)
        }
    }


    internal enum class ConfigurationType {
        KOTLIN_MULTIPLATFORM,
        ANDROID_LIB,
        ANDROID_APP,
        KOTLIN_JVM,
        IDEA_PLUGIN,
    }

    internal companion object {
        internal const val NAME = "fluxoConfiguration"
    }
}
