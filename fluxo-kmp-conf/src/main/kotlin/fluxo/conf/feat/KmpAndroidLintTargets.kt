// Runs only once `com.android.kotlin.multiplatform.library` is applied: fluxo's AGP 9 route
// (AGP 8 KMP modules use the legacy Android plugins). The oldest supported AGP lacks this API.
@file:VersionGated

package fluxo.conf.feat

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import fluxo.annotation.VersionGated
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** The target IS the Lint-carrying extension on the AGP-9 KMP+Android plugin. */
internal fun KotlinMultiplatformExtension.configureKmpAndroidLint(
    conf: FluxoConfigurationExtensionImpl,
    disableLint: Boolean,
    reBaseline: Boolean,
) {
    targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
        lint.configureAndroidLintExtension(
            conf = conf,
            disableLint = disableLint,
            reBaseline = reBaseline,
        )
    }
}
