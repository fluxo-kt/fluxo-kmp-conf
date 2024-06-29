package fluxo.conf.dsl.container.impl.target

import AndroidCommonExtension
import bundleFor
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.AndroidTarget
import fluxo.conf.impl.android.ANDROID_APP_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_EXT_NAME
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
    context: ContainerContext,
    name: String,
) : KmpTargetContainerImpl<KotlinAndroidTarget>(context, name, ANDROID_SORT_ORDER),
    KmpTargetContainerImpl.CommonJvm<KotlinAndroidTarget>,
    AndroidTarget<T>
    where T : AndroidCommonExtension, T : TestedExtension {

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
        setupAndroidCommon(context.conf)
    }


    override fun KotlinMultiplatformExtension.createTarget() = when {
        /** `androidTarget` should be used since Kotlin 1.9.0 instead of `android`. */
        // https://kotl.in/android-target-dsl.
        context.kotlinPluginVersion >= KOTLIN_1_9 -> createTarget(::androidTarget)
        else ->
            @Suppress("DEPRECATION")
            createTarget(::android)
    }

    final override fun setup(k: KotlinMultiplatformExtension) {
        val target = k.createTarget()
        val project = target.project
        setupAndroid(project)

        if (!allowManualHierarchy) {
            return
        }

        val layoutV2 = context.ctx.androidLayoutV2
        val bundle = k.sourceSets.bundleFor(target, androidLayoutV2 = layoutV2, isAndroid = true)
        setupParentSourceSet(k, bundle)

        /**
         * Configure Android's variants,
         * source sets for them are added later.
         *
         * @see org.jetbrains.kotlin.gradle.utils.forAllAndroidVariants
         * @see org.jetbrains.kotlin.gradle.plugin.AndroidProjectHandler
         */
        val classifier = target.disambiguationClassifier // android
        k.sourceSets.configureEach s@{
            val name = name
            val isVariantAndroidSourceSet = name.startsWith(classifier) &&
                "Native" !in name && // exclude `androidNative`
                this !in bundle
            if (!isVariantAndroidSourceSet) {
                return@s
            }

            // TODO: should androidUnitTestDebug depend on androidUnitTest?
            // TODO: provide a `setupParentSourceSet` with a single SourceSet arg

            val m: KotlinSourceSet
            val t: KotlinSourceSet
            if (isTestRelated()) {
                m = bundle.main
                t = this
            } else {
                m = this
                t = bundle.test
            }

            val variantBundle = SourceSetBundle(main = m, test = t, isAndroid = true)
            setupParentSourceSet(k, variantBundle)
        }
    }


    interface Configure : AndroidTarget.Configure, ContainerHolderAware {

        override fun androidApp(
            targetName: String,
            configure: AndroidTarget<BaseAppModuleExtension>.() -> Unit,
        ) {
            holder.configure(targetName, ::App, KmpTargetCode.ANDROID, configure)
        }

        override fun androidLibrary(
            targetName: String,
            configure: AndroidTarget<LibraryExtension>.() -> Unit,
        ) {
            holder.configure(targetName, ::Library, KmpTargetCode.ANDROID, configure)
        }
    }

    class App(context: ContainerContext, targetName: String) :
        TargetAndroidContainer<BaseAppModuleExtension>(context, targetName) {

        init {
            applyPlugins(ANDROID_APP_PLUGIN_ID)
        }

        override fun setupAndroid(project: Project) {
            project.configureExtension<BaseAppModuleExtension>(ANDROID_EXT_NAME) {
                setupAndroidExtension()
                lazyAndroid.configureEach { this() }
            }
        }
    }

    class Library(context: ContainerContext, targetName: String) :
        TargetAndroidContainer<LibraryExtension>(context, targetName) {

        init {
            applyPlugins(ANDROID_LIB_PLUGIN_ID)
        }

        override fun setupAndroid(project: Project) {
            project.configureExtension<LibraryExtension>(ANDROID_EXT_NAME) {
                setupAndroidExtension()
                lazyAndroid.configureEach { this() }
            }
        }
    }
}
