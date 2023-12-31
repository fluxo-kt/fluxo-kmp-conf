package fluxo.conf.impl.kotlin

import com.google.devtools.ksp.gradle.KspExtension
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.exclude
import fluxo.conf.impl.the
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.PluginAware
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension


internal val PluginAware.hasKsp: Boolean get() = pluginManager.hasPlugin(KSP_PLUGIN_ID)

internal val PluginAware.hasKapt: Boolean get() = pluginManager.hasPlugin(KAPT_PLUGIN_ID)

internal val PluginAware.hasKmpCompose: Boolean
    get() = pluginManager.hasPlugin(JETBRAINS_COMPOSE_PLUGIN_ID)


internal val Project.mppExt: KotlinMultiplatformExtension
    get() = mppExtOrNull ?: error("Could not find KotlinMultiplatformExtension ($project)")

internal val Project.mppExtOrNull: KotlinMultiplatformExtension?
    get() = kotlinExtension as KotlinMultiplatformExtension?

internal val Project.javaSourceSets: SourceSetContainer
    get() = when {
        GradleVersion.current() >= GradleVersion.version("7.1") ->
            the<JavaPluginExtension>().sourceSets

        else -> {
            @Suppress("DEPRECATION")
            convention.getPlugin(org.gradle.api.plugins.JavaPluginConvention::class.java)
                .sourceSets
        }
    }


internal fun Project.ksp(action: KspExtension.() -> Unit) =
    configureExtension("ksp", action = action)


internal val excludeJetbrainsAnnotations: ExternalModuleDependency.() -> Unit = {
    exclude(group = "org.jetbrains", module = "annotations")
}
