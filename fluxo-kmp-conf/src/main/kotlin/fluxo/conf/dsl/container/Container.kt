package fluxo.conf.dsl.container

import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency

/**
 * Generic lazy configuration container.
 */
public interface Container : Comparable<Container> {

    /**
     * Adds plugins by ID to the project along with the container configuration.
     *
     * @see org.gradle.api.plugins.PluginManager.apply
     * @see org.gradle.api.plugins.PluginContainer.apply
     */
    public fun applyPlugins(vararg pluginIds: String)

    /**
     * Adds plugins by dependency to the project along with the container configuration.
     * Convenient for usage with Gradle Version Catalogs.
     *
     * @see org.gradle.api.plugins.PluginManager.apply
     * @see org.gradle.api.plugins.PluginContainer.apply
     */
    public fun applyPlugins(vararg plugins: Provider<PluginDependency>)
}
