package fluxo.conf.dsl.container.impl

import fluxo.annotation.InternalFluxoApi
import fluxo.conf.dsl.container.Container
import fluxo.conf.impl.set
import org.gradle.api.Named
import org.gradle.api.plugins.PluginManager

@InternalFluxoApi
internal abstract class ContainerImpl(
    val context: ContainerContext,
) : Container, Named, Comparable<ContainerImpl> {

    // region utility

    abstract val sortOrder: Byte

    final override fun compareTo(other: ContainerImpl): Int = sortOrder.compareTo(other.sortOrder)

    final override fun hashCode(): Int = name.hashCode()

    final override fun equals(other: Any?): Boolean {
        return this === other || other is ContainerImpl && name == other.name
    }

    // endregion


    // region plugins configuration

    private val pluginIds = context.objects.set<String>()

    fun applyPluginsWith(pluginManager: PluginManager) {
        pluginIds.all {
            pluginManager.apply(this)
        }
    }

    /**
     * Adds plugins by ID to the project along with the configuration.
     *
     * @see org.gradle.api.plugins.PluginManager.apply
     * @see org.gradle.api.plugins.PluginContainer.apply
     */
    override fun applyPlugins(vararg pluginIds: String) {
        this.pluginIds.addAll(pluginIds)
    }

    // endregion


    internal companion object {
        // region sort order values

        internal const val ANDROID_SORT_ORDER: Byte = 1
        internal const val JVM_SORT_ORDER: Byte = 2

        internal const val JS_SORT_ORDER: Byte = 11
        internal const val WASM_SORT_ORDER: Byte = 12

        internal const val ANDROID_NATIVE_SORT_ORDER: Byte = 21

        internal const val APPLE_IOS_SORT_ORDER: Byte = 31
        internal const val APPLE_MACOS_SORT_ORDER: Byte = 32
        internal const val APPLE_TVOS_SORT_ORDER: Byte = 33
        internal const val APPLE_WATCHOS_SORT_ORDER: Byte = 34

        internal const val LINUX_SORT_ORDER: Byte = 41

        internal const val MINGW_SORT_ORDER: Byte = 51

        internal const val WASM_NATIVE_SORT_ORDER: Byte = 61

        internal const val CUSTOM_SORT_ORDER: Byte = 101

        // endregion
    }
}
