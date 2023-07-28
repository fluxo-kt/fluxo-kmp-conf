package fluxo.conf.dsl.container.impl.target

import bundleFor
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.AndroidTarget
import fluxo.conf.impl.android.ANDROID_APP_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_LIB_PLUGIN_ID
import fluxo.conf.impl.android.setupAndroidCommon
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.container
import fluxo.conf.impl.isTestRelated
import fluxo.conf.impl.kotlin.KOTLIN_1_9
import fluxo.conf.impl.set
import fluxo.conf.kmp.SourceSetBundle
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

internal abstract class TargetAndroidContainer<T>(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<KotlinAndroidTarget>(context, name, ANDROID_SORT_ORDER),
    KmpTargetContainerImpl.CommonJvm<KotlinAndroidTarget>, AndroidTarget<T>
    where T : CommonExtension<*, *, *, *, *>, T : TestedExtension {

    internal val lazyAndroid = context.objects.set<T.() -> Unit>()

    override fun onAndroidExtension(action: T.() -> Unit) {
        lazyAndroid.add(action)
    }


    private val lazySourceSetTestInstrumented =
        context.objects.container<KotlinSourceSet.() -> Unit>()

    // FIXME: Implement API for source sets.
    override fun sourceSetTestInstrumented(action: KotlinSourceSet.() -> Unit) {
        lazySourceSetTestInstrumented.add(action)
    }


    internal abstract fun setupAndroid(project: Project)

    protected fun T.setupAndroidExtension() {
        // Set before executing action so that they may be overridden if desired.
        setupAndroidCommon(context)
    }


    override fun KotlinMultiplatformExtension.createTarget() = when {
        /** `androidTarget` should be used since Kotlin 1.9.0 instead of `android`. */
        // https://kotl.in/android-target-dsl.
        context.kotlinPluginVersion >= KOTLIN_1_9 -> createTarget(::androidTarget)
        else -> @Suppress("DEPRECATION") createTarget(::android)
    }

    final override fun setup(k: KotlinMultiplatformExtension) {
        val target = k.createTarget()
        val project = target.project
        setupAndroid(project)
        val layoutV2 = context.context.androidLayoutV2
        val bundle = k.sourceSets.bundleFor(target, androidLayoutV2 = layoutV2)
        setupParentSourceSet(k, bundle)

        /**
         * Configure Android's variants
         *
         * @see org.jetbrains.kotlin.gradle.utils.forAllAndroidVariants
         * @see org.jetbrains.kotlin.gradle.plugin.AndroidProjectHandler
         */
        val disambiguationClassifier = target.disambiguationClassifier
        k.sourceSets.all {
            if (name.startsWith(disambiguationClassifier) && this !in bundle) {
                // TODO: should androidUnitTestDebug depend on androidUnitTest?
                // TODO: provide a `setupParentSourceSet` with a single SourceSet arg
                val variantBundle = when {
                    isTestRelated() -> SourceSetBundle(main = bundle.main, test = this)
                    else -> SourceSetBundle(main = this, test = bundle.test)
                }
                setupParentSourceSet(k, variantBundle)
            }
        }
    }


    interface Configure : AndroidTarget.Configure, ContainerHolderAware {

        override fun androidApp(
            targetName: String,
            action: AndroidTarget<BaseAppModuleExtension>.() -> Unit,
        ) {
            holder.configure(targetName, ::App, KmpTargetCode.ANDROID, action)
        }

        override fun androidLibrary(
            targetName: String,
            action: AndroidTarget<LibraryExtension>.() -> Unit,
        ) {
            holder.configure(targetName, ::Library, KmpTargetCode.ANDROID, action)
        }
    }

    class App(context: ContainerContext, targetName: String) :
        TargetAndroidContainer<BaseAppModuleExtension>(context, targetName) {

        init {
            applyPlugins(ANDROID_APP_PLUGIN_ID)
        }

        override fun setupAndroid(project: Project) {
            project.configureExtension<BaseAppModuleExtension>("android") {
                setupAndroidExtension()
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
            project.configureExtension<LibraryExtension>("android") {
                setupAndroidExtension()
                lazyAndroid.all { this() }
            }
        }
    }
}
