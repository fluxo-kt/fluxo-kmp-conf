package fluxo.conf.deps

import fluxo.conf.impl.checkIsRootProject
import fluxo.conf.impl.d
import fluxo.conf.impl.l
import fluxo.conf.impl.withType
import java.io.Serializable
import java.net.URLClassLoader
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin.CLEAN_TASK_NAME

internal object FluxoCache {

    @Volatile
    private var logger: Logger? = null

    /**
     * Clear the [FluxoCache] when the user does a clean
     *
     * @TODO: Make it a task?
     */
    internal fun bindToProjectLifecycle(project: Project) {
        project.checkIsRootProject("FluxoCache.bindToProjectLifecycle")
        this.logger = project.logger

        val cacheKey = System.identityHashCode(project)
        val clearFluxoCache = Action<Task> {
            doLast {
                clearOnce(cacheKey)
            }
        }
        project.allprojects {
            plugins.withType<LifecycleBasePlugin> {
                tasks.named(CLEAN_TASK_NAME)
                    .configure(clearFluxoCache)
            }
        }
    }


    private val cache = HashMap<SerializedKey, URLClassLoader>()

    fun classloader(state: JarState) = classloader(state, state)

    @Synchronized
    fun classloader(key: Serializable, state: JarState): ClassLoader {
        return cache.computeIfAbsent(SerializedKey(key)) {
            logger?.d(
                "Allocating an additional ClassLoader for key={} Cache.size was {}",
                key,
                cache.size,
            )
            URLClassLoader(state.jarUrls(), javaClass.classLoader)
        }
    }


    /**
     * Closes all cached classloaders.
     */
    private fun clear() {
        synchronized(this) {
            val values = ArrayList(cache.values)
            cache.clear()
            values
        }.run {
            forEach { it?.close() }
            if (isNotEmpty()) {
                logger?.l("Cleared $size dynamic plugins classloaders")
            }
        }
    }

    @Volatile
    private var lastClear: Any? = null

    fun clearOnce(key: Any?): Boolean {
        synchronized(this) {
            if (key != null && key == lastClear) {
                return false
            }
            lastClear = key
        }
        clear()
        return true
    }


    internal class SerializedKey(key: Serializable) {
        val serialized: ByteArray
        private val hashCode: Int

        init {
            serialized = LazyForwardingEquality.toBytes(key)
            hashCode = serialized.contentHashCode()
        }

        override fun equals(other: Any?) =
            (other is SerializedKey && serialized.contentEquals(other.serialized))

        override fun hashCode() = hashCode
    }
}
