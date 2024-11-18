package fluxo.conf.dsl.impl

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.container.KmpConfigurationContainerDsl as KmpDsl
import fluxo.conf.dsl.container.KotlinConfigurationContainerDsl as KstDsl
import fluxo.conf.dsl.container.impl.ContainerHolder
import fluxo.conf.dsl.container.impl.KmpConfigurationContainerDslImpl
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.dsl.impl.ConfigurationType.ANDROID_APP
import fluxo.conf.dsl.impl.ConfigurationType.ANDROID_LIB
import fluxo.conf.dsl.impl.ConfigurationType.GRADLE_PLUGIN
import fluxo.conf.dsl.impl.ConfigurationType.IDEA_PLUGIN
import fluxo.conf.dsl.impl.ConfigurationType.KOTLIN_JVM
import fluxo.conf.dsl.impl.ConfigurationType.KOTLIN_MULTIPLATFORM
import fluxo.conf.impl.kotlin.KotlinConfig
import fluxo.conf.impl.uncheckedCast
import fluxo.log.i
import java.lang.System.currentTimeMillis
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** @see fluxo.conf.FluxoKmpConfPlugin.configureContainers */
internal typealias ConfigureContainers =
    (FluxoConfigurationExtensionImpl, Collection<Container>) -> Unit

@FluxoKmpConfDsl
internal abstract class FluxoConfigurationExtensionImpl
@Inject internal constructor(
    override val ctx: FluxoKmpConfContext,

    /** @see fluxo.conf.FluxoKmpConfPlugin.configureContainers */
    private val configureContainers: ConfigureContainers,
) : FluxoConfigurationExtension,
    FluxoConfigurationExtensionKotlinImpl,
    FluxoConfigurationExtensionAndroidImpl,
    FluxoConfigurationExtensionPublicationImpl {

    @Volatile
    private var parentCache: FluxoConfigurationExtensionImpl? = null


    @get:Inject
    abstract override val project: Project

    @get:Inject
    abstract override val objects: ObjectFactory

    override val parent: FluxoConfigurationExtensionImpl?
        get() {
            return parentCache ?: project.parent?.fluxoConfiguration
                ?.also { parentCache = it }
        }


    @get:Input
    protected abstract val configurationTypeProperty: Property<ConfigurationType>

    /**
     * Current project configuration type.
     */
    internal val mode: ConfigurationType
        get() = configurationTypeProperty.get()


    @get:Input
    @get:Internal
    protected abstract val kotlinConfigProp: Property<KotlinConfig>
    internal var kotlinConfig: KotlinConfig
        get() = kotlinConfigProp.get()
        set(value) = kotlinConfigProp.set(value)


    @get:Input
    protected abstract val defaultConfiguration: Property<(KmpDsl.() -> Unit)?>


    @get:Input
    protected abstract val skipDefaultConfigurationsProp: Property<Boolean>
    override var skipDefaultConfigurations: Boolean
        get() = skipDefaultConfigurationsProp.orNull == true
        set(value) = skipDefaultConfigurationsProp.set(value)


    override fun defaults(action: KmpDsl.() -> Unit) {
        defaultConfiguration.set(action)
    }


    private fun configureAs(type: ConfigurationType, action: KmpDsl.() -> Unit) {
        configurationTypeProperty.apply {
            if (isPresent) {
                throw GradleException("${type.builderMethod} can only be invoked once")
            }
            set(type)
            disallowChanges()
        }

        val start = currentTimeMillis()
        val onlyTarget = when (type) {
            KOTLIN_MULTIPLATFORM -> null
            ANDROID_LIB, ANDROID_APP -> KmpTargetCode.ANDROID
            KOTLIN_JVM, GRADLE_PLUGIN, IDEA_PLUGIN -> KmpTargetCode.JVM
        }

        val holder = ContainerHolder(conf = this, onlyTarget)
        val dsl = KmpConfigurationContainerDslImpl(holder)

        // Default configurations
        applyDefaultKmpConfigurations(dsl)

        // Default single target configuration
        if (onlyTarget != null) {
            when (type) {
                ANDROID_LIB -> dsl.androidLibrary()
                ANDROID_APP -> dsl.androidApp()
                else -> dsl.jvm()
            }
        }

        // User configuration
        action(dsl)

        val containers = holder.containers.sorted()

        /** @see fluxo.conf.FluxoKmpConfPlugin.configureContainers */
        configureContainers(this, containers)

        val elapsed = currentTimeMillis() - start
        project.logger.i(":${type.builderMethod} configuration took $elapsed ms")
    }

    override fun asKmp(action: KmpDsl.() -> Unit) =
        configureAs(KOTLIN_MULTIPLATFORM, action)

    override fun asJvm(action: KstDsl<KotlinJvmProjectExtension>.() -> Unit) =
        configureAs(KOTLIN_JVM, uncheckedCast(action))

    override fun asIdeaPlugin(action: KstDsl<KotlinJvmProjectExtension>.() -> Unit) =
        configureAs(IDEA_PLUGIN, uncheckedCast(action))

    override fun asGradlePlugin(action: KstDsl<KotlinJvmProjectExtension>.() -> Unit) =
        configureAs(GRADLE_PLUGIN, uncheckedCast(action))

    override fun asAndroid(app: Boolean, action: KstDsl<KotlinAndroidProjectExtension>.() -> Unit) =
        configureAs(if (app) ANDROID_APP else ANDROID_LIB, uncheckedCast(action))


    private fun applyDefaultKmpConfigurations(dsl: KmpConfigurationContainerDslImpl) {
        // Collect all defaults
        val defaults = mutableListOf<FluxoConfigurationExtension>()
        var p: Project? = project
        var ext: FluxoConfigurationExtensionImpl? = this
        @Suppress("LoopWithTooManyJumpStatements")
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
                ?.defaultConfiguration?.orNull
                ?.invoke(dsl)
        }
    }
}
