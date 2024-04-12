@file:Suppress("UnstableApiUsage")

package fluxo.conf.impl.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.builder.model.BaseConfig
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.addAndLog
import fluxo.conf.impl.the
import fluxo.vc.onLibrary
import org.gradle.api.Project

/**
 * Finalizes DSL for `androidComponents` extension.
 *
 * Customizes the DSL Objects after they've been evaluated from the build files
 * and before used in the build process next steps like variant or tasks creation.
 *
 * [Documentation](https://developer.android.com/studio/build/extend-agp)
 *
 * @see com.android.build.api.variant.AndroidComponentsExtension
 * @see com.android.build.api.variant.DslLifecycle.finalizeDsl
 */
internal fun Project.setupFinalizeAndroidDsl(ctx: FluxoKmpConfContext) {
    val libs = ctx.libs
    val isMaxDebug = ctx.isMaxDebug

    val enableMaxDebug = isMaxDebug.toString()
    the(AndroidComponentsExtension::class).finalizeDsl { a ->
        val buildConfigIsRequired = a.buildFeatures.buildConfig == true || a.buildTypes.any {
            /** @see com.android.build.gradle.internal.dsl.BuildType */
            (it as BaseConfig).buildConfigFields.isNotEmpty()
        }
        if (buildConfigIsRequired) {
            a.buildFeatures.buildConfig = true
        }

        a.defaultConfig {
            minSdk?.let { minSdk ->
                @Suppress("MagicNumber")
                when {
                    // Don't rasterize vector drawables (androidMinSdk >= 21)
                    minSdk >= 21 -> vectorDrawables.generatedDensities()

                    // Use runtime support for the vector drawables, instead of build-time support.
                    else -> vectorDrawables.useSupportLibrary = true
                }
            }
        }

        val isApplication = a is ApplicationExtension
        if (!isApplication && !buildConfigIsRequired) {
            return@finalizeDsl
        }
        for (bt in a.buildTypes) {
            val isReleaseBuildType = bt.name == RELEASE

            // Add leakcanary to all build types in the app
            if (isApplication && !isReleaseBuildType && bt.name != DEBUG) {
                libs.onLibrary(ALIAS_LEAK_CANARY) { d ->
                    dependencies.addAndLog("${bt.name}Implementation", d)
                }
            }

            if (buildConfigIsRequired) {
                val boolean = "boolean"
                val enableTest = if (isReleaseBuildType) enableMaxDebug else "true"
                bt.buildConfigField(boolean, "TEST", enableTest)
                bt.buildConfigField(boolean, "MAX_DEBUG", enableMaxDebug)
            }
        }
    }
}
