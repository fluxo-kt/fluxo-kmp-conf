// Runs only once `com.android.kotlin.multiplatform.library` is applied: fluxo's AGP 9 route
// (AGP 8 KMP modules use the legacy Android plugins). The oldest supported AGP lacks this API.
@file:VersionGated

package fluxo.conf.feat

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import fluxo.annotation.VersionGated
import fluxo.conf.impl.kotlin.mppExtOrNull
import fluxo.conf.impl.namedCompat
import fluxo.conf.impl.withType
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

/**
 * Detekt 1.x gives a KMP compilation's type-resolution tasks `compileDependencyFiles`, the raw
 * configuration. For the AGP 9 KMP Android target that configuration can't be resolved as is:
 * with `withHostTest {}` the host-test classpath sees several variants of the module itself
 * ("cannot choose between the following variants"), so `check` fails before running anything.
 * The Kotlin compile task's `libraries` hold what the compiler actually used, resolved by AGP
 * and KGP, which is exactly what type resolution needs. Other targets resolve fine and keep
 * Detekt's own classpath. Detekt 2 takes `libraries` itself.
 */
internal fun Project.useCompilerClasspathInKmpAndroidDetekt() {
    val kotlin = mppExtOrNull ?: return
    kotlin.targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
        val target = name.capitalized()
        compilations.configureEach {
            val compilation = this
            val suffix = target + compilation.name.capitalized()
            val libraries = files(
                compilation.compileTaskProvider.map { (it as KotlinCompileTool).libraries },
            )
            tasks.namedCompat<Task, Detekt> { it == DETEKT_TASK_NAME + suffix }
                .configureEach { classpath.setFrom(compilation.output.classesDirs, libraries) }
            val baselineName = DETEKT_BASELINE_TASK_NAME + suffix
            tasks.namedCompat<Task, DetektCreateBaselineTask> { it == baselineName }
                .configureEach { classpath.setFrom(compilation.output.classesDirs, libraries) }
        }
    }
}
