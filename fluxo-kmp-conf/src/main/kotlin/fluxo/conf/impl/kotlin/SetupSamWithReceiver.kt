package fluxo.conf.impl.kotlin

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.KOTLIN_SAM_RECEIVER_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.KOTLIN_SAM_RECEIVER_PLUGIN_ID
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.log.e
import org.gradle.api.HasImplicitReceiver
import org.gradle.api.Project
import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension

internal fun Project.setupSamWithReceiver(ctx: FluxoKmpConfContext) {
    val result = ctx.loadAndApplyPluginIfNotApplied(
        id = KOTLIN_SAM_RECEIVER_PLUGIN_ID,
        className = KT_SAM_RECEIVER_PLUGIN_CLASS,
        version = runCatching { KOTLIN_PLUGIN_VERSION_RAW }.getOrNull(),
        catalogPluginId = KOTLIN_SAM_RECEIVER_PLUGIN_ALIAS,
        catalogVersionId = "kotlin",
        project = this,
    )
    if (!result.applied) {
        return
    }

    // Gradle kotlin-dsl like setup. See:
    // https://github.com/gradle/gradle/blob/4817230/build-logic/kotlin-dsl/src/main/kotlin/gradlebuild.kotlin-dsl-sam-with-receiver.gradle.kts#L22
    try {
        val swre = extensions.getByName(KT_SAM_RECEIVER_EXTENSION)
        val hasImplicitReceiver = requireNotNull(HasImplicitReceiver::class.qualifiedName)
        try {
            (swre as SamWithReceiverExtension).annotation(hasImplicitReceiver)
        } catch (ncde: NoClassDefFoundError) {
            // Reflection-based fallback.
            // Required when plugin is loaded dynamically.
            try {
                swre.javaClass.getMethod("annotation", String::class.java)
                    .invoke(swre, hasImplicitReceiver)
            } catch (e: Throwable) {
                e.addSuppressed(ncde)
                throw e
            }
        }
    } catch (e: Throwable) {
        logger.e("Couldn't configure samWithReceiver extension due to: $e", e)
    }
}


/** @see org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverGradleSubplugin */
private const val KT_SAM_RECEIVER_PLUGIN_CLASS =
    "org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverGradleSubplugin"

private const val KT_SAM_RECEIVER_EXTENSION = "samWithReceiver"
