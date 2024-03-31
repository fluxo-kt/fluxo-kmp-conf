package fluxo.conf.impl

import com.sun.management.OperatingSystemMXBean
import fluxo.conf.impl.kotlin.JRE_21
import fluxo.conf.impl.kotlin.JRE_VERSION
import java.lang.management.ManagementFactory
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.PluginAware


internal val CPUs: Int = Runtime.getRuntime().availableProcessors()

/** The maximum amount of memory that the Java virtual machine will attempt to use. */
internal val XMX: Long = Runtime.getRuntime().maxMemory()

internal val TOTAL_OS_MEMORY: Long = run {
    try {
        val bean = ManagementFactory
            .getPlatformMXBean(OperatingSystemMXBean::class.java)
        bean?.apply {
            var ex: Throwable? = null

            // It's still available and works up to JRE 21,
            // but deprecated since JRE 14.
            if (JRE_VERSION <= JRE_21) {
                try {
                    @Suppress("DEPRECATION")
                    return@run totalPhysicalMemorySize
                } catch (e: Throwable) {
                    ex = e
                }
            }

            return@run try {
                // New method since JRE 14+
                // Use reflection to avoid compilation errors on older JDKs.
                /** @see OperatingSystemMXBean.getTotalMemorySize */
                javaClass.getMethod("getTotalMemorySize").invoke(this) as Long
            } catch (e: Throwable) {
                try {
                    // Fallback to deprecated method
                    @Suppress("DEPRECATION")
                    totalPhysicalMemorySize
                } catch (th: Throwable) {
                    ex?.let { th.addSuppressed(it) }
                    ex?.let { th.addSuppressed(e) }
                    throw th
                }
            }
        }
    } catch (_: Throwable) {
    }
    return@run -1
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
