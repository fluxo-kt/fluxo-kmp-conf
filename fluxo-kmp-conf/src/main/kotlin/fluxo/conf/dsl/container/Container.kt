package fluxo.conf.dsl.container

/**
 * Generic lazy configuration container.
 */
public interface Container {

    /**
     * Adds plugins by ID to the project along with the container configuration.
     *
     * @see org.gradle.api.plugins.PluginManager.apply
     * @see org.gradle.api.plugins.PluginContainer.apply
     */
    public fun applyPlugins(vararg pluginIds: String)
}
