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
import fluxo.conf.impl.android.ANDROID_KMP_LIB_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_LIB_PLUGIN_ID
import fluxo.conf.impl.android.AgpVersion
import fluxo.conf.impl.android.setupAndroidCommon
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.container
import fluxo.conf.impl.isTestRelated
import fluxo.conf.impl.kotlin.KOTLIN_1_9
import fluxo.conf.impl.kotlin.KOTLIN_2_0
import fluxo.conf.impl.set
import fluxo.conf.kmp.SourceSetBundle
import fluxo.log.e
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
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


    /**
     * `androidTarget` replaced the old `android()` factory in Kotlin 1.9.0;
     * `android()` itself was removed in KGP 2.x. Build Kotlin is now 2.2+,
     * so the legacy fallback is unreachable and would no longer compile.
     *
     * @see <a href="https://kotl.in/android-target-dsl">Android target DSL</a>
     */
    override fun KotlinMultiplatformExtension.createTarget() =
        createTarget(::androidTarget)

    final override fun setup(k: KotlinMultiplatformExtension) {
        if (this is Library && this.isAgp9OrLater) {
            // The AGP-9 KMP+Android plugin (`com.android.kotlin.multiplatform.library`)
            // auto-creates the `android` KMP target via `kotlin { android { } }`. The legacy
            // `createTarget(::androidTarget)` call below would crash with "target 'android'
            // already exists, but was not created with the 'android' preset" — the new
            // target is `KotlinMultiplatformAndroidLibraryTarget`, disjoint from
            // `KotlinAndroidTarget` (this container's type parameter) and managed by its own
            // source-set layout. Post-extension wiring is delivered by `setupKmpAndroidExtension`
            // (namespace/compileSdk/minSdk/buildToolsVersion) and `setupKmpAndroidLint`
            // (Lint config). Only the consumer-facing warning of `setupAndroid` runs here.
            setupAndroid(context.project)
            return
        }
        val target = k.createTarget()
        val project = target.project
        val ctx = context.ctx

        // Use a usual test source set tree for Android unit tests.
        // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html#writing-and-running-tests-with-compose-multiplatform
        @Suppress("OPT_IN_USAGE")
        try {
            target.unitTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
            target.instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
        } catch (e: Throwable) {
            if (ctx.kotlinPluginVersion >= KOTLIN_2_0) {
                project.logger.e(
                    "Failed to set unitTest and instrumentedTest" +
                        " source set trees for Android to `test`",
                    e,
                )
            }
        }

        setupAndroid(project)

        val layoutV2 = ctx.androidLayoutV2
        val bundle = k.sourceSets.bundleFor(target, androidLayoutV2 = layoutV2, isAndroid = true)
        setupParentSourceSet(k, bundle)
        if (!allowManualHierarchy) {
            return
        }

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
            // AGP 9 has no KMP+Android equivalent of `com.android.application` (only
            // `com.android.kotlin.multiplatform.library`). KMP+Android-app modules cannot
            // be expressed under AGP 9 and must restructure (split a thin AGP-9 KMP library
            // beneath a non-KMP `com.android.application` consumer module). Fail-fast at
            // container init rather than letting AGP reject the co-application later.
            if (AgpVersion.isAgp9OrLater(context.project)) {
                error(
                    "AGP 9+ rejects `$ANDROID_APP_PLUGIN_ID` + " +
                        "`kotlin(\"multiplatform\")` co-application, and there is no " +
                        "KMP-aware AGP application plugin. KMP+Android-app modules must " +
                        "split into a KMP library (this module, swap to `androidLibrary { … }`) " +
                        "consumed by a separate non-KMP `com.android.application` module.",
                )
            }
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

        /**
         * `true` when the build classpath has AGP `>= 9.0` — controls whether this container
         * applies the legacy [ANDROID_LIB_PLUGIN_ID] (AGP 8.x) or the AGP-9 KMP-aware
         * [ANDROID_KMP_LIB_PLUGIN_ID]. Snapshotted at init time (before `applyPlugins`
         * queues the id); `setupAndroid` re-uses the same flag so init and configuration
         * agree on which line is active.
         */
        internal val isAgp9OrLater: Boolean = AgpVersion.isAgp9OrLater(context.project)

        init {
            applyPlugins(if (isAgp9OrLater) ANDROID_KMP_LIB_PLUGIN_ID else ANDROID_LIB_PLUGIN_ID)
        }

        override fun setupAndroid(project: Project) {
            if (isAgp9OrLater) {
                // The AGP-9 KMP+Android plugin auto-creates the `android` KMP target via
                // `kotlin { android { } }` and does NOT register a top-level `android`
                // Gradle extension. `FluxoConfigurationExtension` slot-fills (`namespace`,
                // `compileSdk`, `minSdk`, `buildToolsVersion`) are auto-applied by
                // `setupKmpAndroidExtension` (wired in `setupKotlin`); lint by
                // `setupKmpAndroidLint` (wired in `setupVerification`). `lazyAndroid`
                // lambdas target the disjoint `LibraryExtension` type and cannot run on
                // `KotlinMultiplatformAndroidLibraryExtension` — warn the consumer if any
                // were queued so the silent-skip is observable, but stay GREEN otherwise.
                if (!lazyAndroid.isEmpty()) {
                    project.logger.e(
                        "AGP 9+: `onAndroidExtension { … }` lambdas registered on " +
                            "`androidLibrary { … }` for target `$name` are ignored — " +
                            "they target the legacy `LibraryExtension`, replaced under " +
                            "AGP 9 by the disjoint `KotlinMultiplatformAndroidLibraryExtension`. " +
                            "Move per-target config to `kotlin { android { … } }` directly, " +
                            "or to `fluxoConfiguration { androidNamespace = …, " +
                            "androidCompileSdk = …, androidMinSdk = … }`.",
                    )
                }
                return
            }
            project.configureExtension<LibraryExtension>(ANDROID_EXT_NAME) {
                setupAndroidExtension()
                lazyAndroid.configureEach { this() }
            }
        }
    }
}
