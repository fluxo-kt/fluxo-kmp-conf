package fluxo.conf.dsl.container.impl.target

import ANDROID_APP_PLUGIN_ID
import ANDROID_LIB_PLUGIN_ID
import bundleFor
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetAndroid
import fluxo.conf.impl.KOTLIN_1_9
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.container
import fluxo.conf.impl.set
import fluxo.conf.kmp.KmpTargetCode
import fluxo.conf.kmp.SourceSetBundle
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

@Suppress("UnstableApiUsage")
internal abstract class TargetAndroidContainer<T : TestedExtension>(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<KotlinAndroidTarget>(
    context, name, KmpTargetCode.ANDROID, ANDROID_SORT_ORDER,
), KmpTargetContainerImpl.CommonJvm<KotlinAndroidTarget>, TargetAndroid<T> {

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

    protected fun T.setupAndroidExtension() {
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
    }


    override fun KotlinMultiplatformExtension.createTarget() = when {
        // `android` replaced with `androidTarget` in Kotlin 1.9.0
        // https://kotl.in/android-target-dsl
        context.kotlinPluginVersion >= KOTLIN_1_9 -> createTarget(::androidTarget)
        else -> @Suppress("DEPRECATION") createTarget(::android)
    }

    final override fun setup(k: KotlinMultiplatformExtension) {
        val target = k.createTarget()
        val project = target.project
        setupAndroid(project)
        val layoutV2 = context.context.androidLayoutV2
        // FIXME: Wait for android variants to be created before configuring source sets,
        //  setup bundle for each variant.
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
                    "Test" in name -> SourceSetBundle(main = bundle.main, test = this)
                    else -> SourceSetBundle(main = this, test = bundle.test)
                }
                setupParentSourceSet(k, variantBundle)
            }
        }
    }


    interface Configure : TargetAndroid.Configure, ContainerHolderAware {

        // TODO: Is it ok to have an android app target in the KMP module?
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
            project.configureExtension<BaseAppModuleExtension> {
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
            project.configureExtension<LibraryExtension> {
                setupAndroidExtension()
                lazyAndroid.all { this() }
            }
        }
    }
}
