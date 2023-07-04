package fluxo.conf.dsl.container.impl.target

import ANDROID_APP_PLUGIN_ID
import ANDROID_LIB_PLUGIN_ID
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KotlinTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetAndroid
import fluxo.conf.impl.KOTLIN_1_8
import fluxo.conf.impl.KOTLIN_1_9
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.container
import fluxo.conf.impl.set
import fluxo.conf.target.KmpTargetCode
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

internal abstract class TargetAndroidContainer<T : TestedExtension>(
    context: ContainerContext, name: String,
) : KotlinTargetContainerImpl<KotlinAndroidTarget>(
    context, name, KmpTargetCode.ANDROID, ANDROID_SORT_ORDER,
), KotlinTargetContainerImpl.CommonJvm<KotlinAndroidTarget>, TargetAndroid<T> {

    internal val lazyAndroid = context.objects.set<T.() -> Unit>()

    override fun android(action: T.() -> Unit) {
        lazyAndroid.add(action)
    }


    private val lazySourceSetTestInstrumented =
        context.objects.container<KotlinSourceSet.() -> Unit>()

    override fun sourceSetTestInstrumented(action: KotlinSourceSet.() -> Unit) {
        lazySourceSetTestInstrumented.add(action)
    }


    protected abstract fun setupAndroid(project: Project)

    override fun KotlinMultiplatformExtension.createTarget() = when {
        // `android` replaced with `androidTarget` in Kotlin 1.9.0
        // https://kotl.in/android-target-dsl
        context.kotlinPluginVersion >= KOTLIN_1_9 -> createTarget(::androidTarget)
        else -> @Suppress("DEPRECATION") createTarget(::android)
    }

    final override fun setup(k: KotlinMultiplatformExtension) {
        val target = k.createTarget()
        val project = target.project

        // Support for androidSourceSetLayout v2
        // https://kotlinlang.org/docs/whatsnew18.html#kotlinsourceset-naming-schema
        val layoutVersion = when {
            context.kotlinPluginVersion >= KOTLIN_1_8 -> {
                project.extraProperties
                    .properties["kotlin.mpp.androidSourceSetLayoutVersion"]
                    ?.toString()
                    ?.toIntOrNull() ?: 1
            }

            else -> 1
        }

        // FIXME:  test dependsOn commonJvmTest, instrumented dependsOn commonJvmTest
        val (test, instrumented) = when (layoutVersion) {
            2 -> Pair("androidUnitTest", "androidInstrumentedTest")
            else -> Pair("androidTest", "androidAndroidTest")
        }

        setupAndroid(project)
    }


    interface Configure : TargetAndroid.Configure, ContainerHolderAware {

        // TODO: Is it ok to have android app target in the KMP module?
        override fun androidApp(
            targetName: String,
            action: TargetAndroid<BaseAppModuleExtension>.() -> Unit,
        ) {
            holder.configure(targetName, ::App, action)
        }

        override fun androidLibrary(
            targetName: String,
            action: TargetAndroid<LibraryExtension>.() -> Unit,
        ) {
            holder.configure(targetName, ::Library, action)
        }
    }

    class App(context: ContainerContext, targetName: String) :
        TargetAndroidContainer<BaseAppModuleExtension>(context, targetName) {

        init {
            applyPlugins(ANDROID_APP_PLUGIN_ID)
        }

        override fun setupAndroid(project: Project) {
            project.configureExtension(BaseAppModuleExtension::class) {
                // Set before executing action so that they may be overridden if desired.
                compileOptions {
                    // FIXME: Replace with full-fledged context-based target configuration
                    compileSourceCompatibility?.let { compatibility ->
                        sourceCompatibility = compatibility
                    }
                    compileTargetCompatibility?.let { compatibility ->
                        targetCompatibility = compatibility
                    }
                }
                lazyAndroid.all { this() }
            }
        }
    }

    class Library(context: ContainerContext, targetName: String) :
        TargetAndroidContainer<LibraryExtension>(context, targetName) {

        init {
            applyPlugins(ANDROID_LIB_PLUGIN_ID)
        }

        override fun setupAndroid(project: Project) {
            project.configureExtension<LibraryExtension> {
                // Set before executing action so that they may be overridden if desired.
                compileOptions {
                    // FIXME: Replace with full-fledged context-based target configuration
                    compileSourceCompatibility?.let { compatibility ->
                        sourceCompatibility = compatibility
                    }
                    compileTargetCompatibility?.let { compatibility ->
                        targetCompatibility = compatibility
                    }
                }
                lazyAndroid.all { this() }
            }
        }
    }
}
