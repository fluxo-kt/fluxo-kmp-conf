package fluxo.conf

import java.util.concurrent.ConcurrentHashMap
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener

/**
 * Adds an [action] to be called when the build is completed
 * and all selected tasks have been executed.
 *
 * It's a replacement for the
 * [Gradle.buildFinished][org.gradle.api.invocation.Gradle.buildFinished] method.
 *
 * @see org.gradle.api.invocation.Gradle.buildFinished
 */
internal fun FluxoKmpConfContext.onBuildFinished(action: () -> Unit) {
    // https://discuss.gradle.org/t/which-method-can-replace-getproject-getgradle-buildfinished/43768/3
    val project = rootProject
    val name = OnBuildFinishedService.NAME
    val ext = project.extensions.extraProperties
    val serviceProvider: Provider<OnBuildFinishedService>
    if (!ext.has(name)) {
        serviceProvider = project.gradle.sharedServices
            .registerIfAbsent(name, OnBuildFinishedService::class.java) {}
        ext.set(name, serviceProvider)
        eventsListenerRegistry.onTaskCompletion(serviceProvider)
    } else {
        @Suppress("UNCHECKED_CAST")
        serviceProvider = ext.get(name) as Provider<OnBuildFinishedService>
    }
    serviceProvider.get().addBuildListener(action)
}

internal abstract class OnBuildFinishedService : BuildService<BuildServiceParameters.None>,
    OperationCompletionListener, AutoCloseable {

    private val buildListeners = ConcurrentHashMap<() -> Unit, Boolean>()

    @Suppress("EmptyFunctionBlock")
    override fun onFinish(event: FinishEvent?) {
    }

    fun addBuildListener(action: () -> Unit) {
        buildListeners[action] = true
    }

    override fun close() {
        buildListeners.apply {
            keys.forEach { it() }
            clear()
        }
    }

    companion object {
        const val NAME = "fluxoOnBuildFinished"
    }
}
