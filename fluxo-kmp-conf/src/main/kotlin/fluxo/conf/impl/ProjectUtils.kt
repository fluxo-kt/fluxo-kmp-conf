package fluxo.conf.impl

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.PluginAware


internal val CPUs: Int = Runtime.getRuntime().availableProcessors()

/** The maximum amount of memory that the Java virtual machine will attempt to use. */
internal val XMX: Long = Runtime.getRuntime().maxMemory()

/** `-1` where the JVM doesn't expose `com.sun.management` (non-HotSpot runtimes). */
internal val TOTAL_OS_MEMORY: Long = try {
    ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)?.totalMemorySize ?: -1
} catch (_: Throwable) {
    -1
}


internal val Project.isRootProject: Boolean
    get() = rootProject === this

internal fun Project.checkIsRootProject(name: String) {
    require(isRootProject) { "$name can only be used on a root project" }
}


internal inline fun Project.dependencies(configuration: DependencyHandler.() -> Unit) =
    dependencies.configuration()


internal fun PluginAware.withPlugin(id: String, action: Action<in AppliedPlugin>) {
    pluginManager.withPlugin(id, action)
}

internal fun PluginAware.withAnyPlugin(vararg ids: String, action: Action<in AppliedPlugin>) {
    ids.forEach { withPlugin(it, action) }
}
