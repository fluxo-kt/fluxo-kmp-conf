package fluxo.conf.dsl.impl

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.container.KmpConfigurationContainerDsl as KmpConfDsl
import fluxo.conf.dsl.container.impl.ContainerHolder
import fluxo.conf.dsl.container.impl.KmpConfigurationContainerDslImpl
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType.ANDROID_APP
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType.ANDROID_LIB
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType.GRADLE_PLUGIN
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType.IDEA_PLUGIN
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType.KOTLIN_JVM
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType.KOTLIN_MULTIPLATFORM
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

internal typealias ConfigureContainers =
    (ConfigurationType, FluxoConfigurationExtensionImpl, Collection<Container>) -> Unit

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
    private var parentCache: FluxoConfigurationExtensionImpl? = null


    @get:Inject
    abstract override val project: Project

    override val parent: FluxoConfigurationExtensionImpl?
        get() {
            return parentCache ?: project.parent?.fluxoConfiguration
                ?.also { parentCache = it }
        }

    @get:Input
    protected abstract val hasConfigurationAction: Property<Boolean?>

    @get:Input
    protected abstract val defaultConfiguration: Property<(KmpConfDsl.() -> Unit)?>


    @get:Input
    protected abstract val skipDefaultConfigurationsProp: Property<Boolean>
    override var skipDefaultConfigurations: Boolean
        get() = skipDefaultConfigurationsProp.orNull == true
        set(value) = skipDefaultConfigurationsProp.set(value)


    override fun defaultConfiguration(action: KmpConfDsl.() -> Unit) {
        defaultConfiguration.set(action)
    }


    private fun configure(type: ConfigurationType, action: KmpConfDsl.() -> Unit) {
        if (hasConfigurationAction.orNull == true) {
            throw GradleException(
                "${FluxoConfigurationExtension.NAME}.configure* can only be invoked once",
            )
        }
        hasConfigurationAction.set(true)

        val onlyTarget = when (type) {
            KOTLIN_MULTIPLATFORM -> null
            ANDROID_LIB, ANDROID_APP -> KmpTargetCode.ANDROID
            KOTLIN_JVM, GRADLE_PLUGIN, IDEA_PLUGIN -> KmpTargetCode.JVM
        }

        val holder = ContainerHolder(configuration = this, onlyTarget)
        val dsl = KmpConfigurationContainerDslImpl(holder)
        applyDefaultKmpConfigurations(dsl)
        action(dsl)
        configureContainers(type, this, holder.containers.sorted())
    }

    override fun configureAsMultiplatform(action: KmpConfDsl.() -> Unit) =
        configure(KOTLIN_MULTIPLATFORM, action)

    override fun configureAsKotlinJvm(action: KmpConfDsl.() -> Unit) =
        configure(KOTLIN_JVM, action)

    override fun configureAsIdeaPlugin(action: KmpConfDsl.() -> Unit) =
        configure(IDEA_PLUGIN, action)

    override fun configureAsGradlePlugin(action: KmpConfDsl.() -> Unit) =
        configure(GRADLE_PLUGIN, action)

    override fun configureAsAndroid(app: Boolean, action: KmpConfDsl.() -> Unit) =
        configure(if (app) ANDROID_APP else ANDROID_LIB, action)


    private fun applyDefaultKmpConfigurations(dsl: KmpConfigurationContainerDslImpl) {
        // Collect all defaults
        val defaults = mutableListOf<FluxoConfigurationExtension>()
        var p: Project? = project
        var ext: FluxoConfigurationExtensionImpl? = this
        chain@ while (p != null) {
            if (ext != null) {
                if (ext.skipDefaultConfigurations) {
                    break
                }
                defaults.add(ext)

                // Prefer cached
                val pExt = ext.parent
                if (pExt != null) {
                    p = pExt.project
                    ext = pExt
                    continue
                }
            }

            p = p.parent ?: break
            ext = p.fluxoConfiguration
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
        GRADLE_PLUGIN,
        IDEA_PLUGIN,
    }
}
