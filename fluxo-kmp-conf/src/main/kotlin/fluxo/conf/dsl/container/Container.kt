package fluxo.conf.dsl.container

import fluxo.conf.impl.container
import org.gradle.api.Named
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

public sealed class Container
protected constructor(
    internal val context: ContainerContext,
    private val name: String,
) : Named {

    override fun getName(): String = name

    internal abstract val sortOrder: Byte

    @Suppress("MagicNumber", "UnusedReceiverParameter")
    internal inline fun <reified T> Container.typeHashCode(): Int =
        17 * 31 + T::class.java.name.hashCode()


    internal abstract fun KotlinMultiplatformExtension.setup()


    public abstract class ConfigurableTarget
    internal constructor(
        context: ContainerContext,
        name: String,
    ) : Container(context, name) {

        private val pluginIds = context.objects.domainObjectSet(String::class.java)

        internal fun ConfigurableTarget.applyPlugins(project: Project) {
            pluginIds.all {
                project.pluginManager.apply(this)
            }
        }

        public fun pluginIds(vararg ids: String) {
            pluginIds.addAll(ids)
        }


        private val lazySourceSetMain =
            context.objects.container<KotlinSourceSet.() -> Unit>()

        internal fun KotlinSourceSet.lazySourceSetMainConf() {
            lazySourceSetMain.all { this() }
        }

        public fun sourceSetMain(action: KotlinSourceSet.() -> Unit) {
            lazySourceSetMain.add(action)
        }


        private val lazySourceSetTest =
            context.objects.container<KotlinSourceSet.() -> Unit>()

        internal fun KotlinSourceSet.lazySourceSetTestConf() {
            lazySourceSetTest.all { this() }
        }

        public fun sourceSetTest(action: KotlinSourceSet.() -> Unit) {
            lazySourceSetTest.add(action)
        }
    }
}
