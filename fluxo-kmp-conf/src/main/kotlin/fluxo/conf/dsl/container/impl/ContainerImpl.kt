package fluxo.conf.dsl.container.impl

import fluxo.conf.dsl.container.Container
import fluxo.conf.impl.set
import org.gradle.api.Named
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency

internal abstract class ContainerImpl(
    val context: ContainerContext,
) : Container, Named {

    // region utility

    abstract val sortOrder: Byte

    final override fun compareTo(other: Container): Int {
        if (this === other || other !is ContainerImpl) return 0
        return sortOrder.compareTo(other.sortOrder)
    }

    final override fun hashCode(): Int = name.hashCode()

    final override fun equals(other: Any?): Boolean {
        return this === other || other is ContainerImpl && name == other.name
    }

    // endregion


    // region plugins configuration

    private val pluginIds = context.objects.set<String>()

    fun applyPluginsWith(pluginManager: PluginManager) {
        pluginIds.configureEach {
            pluginManager.apply(this)
        }
    }

    override fun applyPlugins(vararg pluginIds: String) {
        this.pluginIds.addAll(pluginIds)
    }

    override fun applyPlugins(vararg plugins: Provider<PluginDependency>) {
        plugins.forEach { provider ->
            // TODO: Utilize version available from PluginDependency
            pluginIds.add(provider.get().pluginId)
        }
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

        internal const val CUSTOM_SORT_ORDER: Byte = 101

        // endregion
    }
}
