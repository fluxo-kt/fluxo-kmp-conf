package fluxo.conf.dsl.container.impl.target

import AndroidCommonExtension
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.AndroidTarget
import fluxo.conf.dsl.impl.ConfigurationType.KOTLIN_MULTIPLATFORM
import fluxo.conf.impl.android.ANDROID_APP_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_EXT_NAME
import fluxo.conf.impl.android.ANDROID_KMP_LIB_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_LIB_PLUGIN_ID
import fluxo.conf.impl.android.AgpVersion
import fluxo.conf.impl.android.setupAndroidCommon
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.container
import fluxo.conf.impl.set
import fluxo.log.e
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

internal abstract class TargetAndroidContainer<T : AndroidCommonExtension>(
    context: ContainerContext,
    name: String,
) : KmpTargetContainerImpl<KotlinAndroidTarget>(context, name, ANDROID_SORT_ORDER),
    KmpTargetContainerImpl.CommonJvm<KotlinAndroidTarget>,
    AndroidTarget<T> {

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

    /**
     * `false` when the upstream Android plugin auto-creates the KMP `android` target on the
     * consumer's behalf (AGP-9 KMP+Android: `com.android.kotlin.multiplatform.library` builds
     * the target from `kotlin { android { } }`). In that case `setup` MUST skip the legacy
     * `KotlinMultiplatformExtension.createTarget(::androidTarget)` call — invoking it would
     * collide with the auto-created target ("target 'android' already exists, but was not
     * created with the 'android' preset") because the new plugin uses the disjoint
     * `KotlinMultiplatformAndroidLibraryTarget` type, not `KotlinAndroidTarget`. The default
     * `true` matches the legacy AGP-8 path; subclasses bound to AGP-9-only plugin ids
     * override.
     */
    protected open val needsLegacyTargetCreation: Boolean get() = true

    final override fun setup(k: KotlinMultiplatformExtension) {
        if (!needsLegacyTargetCreation) {
            // Legacy target creation skipped — see [needsLegacyTargetCreation]. Post-extension
            // wiring (namespace/compileSdk/minSdk via `setupKmpAndroidExtension`, Lint via
            // `setupKmpAndroidLint`) is delivered by separate `pluginManager.withPlugin` hooks
            // keyed on the AGP-9 KMP plugin id. Only `setupAndroid` runs here, for the
            // consumer-facing warning when `lazyAndroid` is non-empty.
            setupAndroid(context.project)
            return
        }
        val target = k.createTarget()
        val project = target.project

        // Use a usual test source set tree for Android unit tests.
        // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html#writing-and-running-tests-with-compose-multiplatform
        @Suppress("OPT_IN_USAGE")
        try {
            target.unitTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
            target.instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
        } catch (e: Throwable) {
            // The pre-2.0 KGP silent-swallow guard was dropped with the layer-2 floor
            // bump (consumer KGP 2.0+); both APIs exist unconditionally now.
            project.logger.e(
                "Failed to set unitTest and instrumentedTest" +
                    " source set trees for Android to `test`",
                e,
            )
        }

        setupAndroid(project)
    }


    interface Configure : AndroidTarget.Configure, ContainerHolderAware {

        override fun androidApp(
            targetName: String,
            configure: AndroidTarget<ApplicationExtension>.() -> Unit,
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
        TargetAndroidContainer<ApplicationExtension>(context, targetName) {

        init {
            // The AGP-9 + KMP+app rejection only applies when this container runs in a KMP
            // context — non-KMP `fkcSetupAndroidApp` (which routes through `asAndroid(app=true)`
            // → `ANDROID_APP` configuration type) is unaffected because there is no
            // `kotlin("multiplatform")` plugin to co-apply with `com.android.application`.
            // Stand-alone AGP 9 + `com.android.application` continues to work as before.
            val isKmpContext = context.conf.mode == KOTLIN_MULTIPLATFORM
            if (isKmpContext && AgpVersion.isAgp9OrLater(context.project)) {
                error(
                    "AGP 9+ rejects `$ANDROID_APP_PLUGIN_ID` + " +
                        "`kotlin(\"multiplatform\")` co-application, and there is no " +
                        "KMP-aware AGP application plugin. Restructure: extract the KMP " +
                        "shared code into a separate `$ANDROID_KMP_LIB_PLUGIN_ID` module, " +
                        "and keep the app code (Activity, manifest, etc.) in a thin " +
                        "non-KMP `$ANDROID_APP_PLUGIN_ID` module that depends on it.",
                )
            }
            applyPlugins(ANDROID_APP_PLUGIN_ID)
        }

        override fun setupAndroid(project: Project) {
            project.configureExtension<ApplicationExtension>(ANDROID_EXT_NAME) {
                setupAndroidExtension()
                lazyAndroid.configureEach { this() }
            }
        }
    }

    class Library(context: ContainerContext, targetName: String) :
        TargetAndroidContainer<LibraryExtension>(context, targetName) {

        /**
         * `true` only when **both** of these hold:
         *  - this container is being constructed inside a KMP configuration
         *    (`fkcSetupMultiplatform { androidLibrary { } }` → `asKmp` → `KOTLIN_MULTIPLATFORM`),
         *  - the build classpath carries AGP `>= 9.0`.
         *
         * Non-KMP `fkcSetupAndroidLibrary` always routes through the legacy
         * `com.android.library` path regardless of AGP version: that plugin still works
         * standalone under AGP 9, and applying the KMP+Android library plugin in a non-KMP
         * project causes AGP to error with `'com.android.kotlin.multiplatform.library' and
         * 'com.android.library' plugins cannot be applied in the same project` (the legacy
         * id gets in via consumer / parent-config defaults). Snapshotted at init time;
         * `setupAndroid` and [needsLegacyTargetCreation] re-use the same flag so init, target
         * creation, and configuration agree on which line is active.
         */
        private val useAgp9KmpLine: Boolean =
            context.conf.mode == KOTLIN_MULTIPLATFORM &&
                AgpVersion.isAgp9OrLater(context.project)

        override val needsLegacyTargetCreation: Boolean get() = !useAgp9KmpLine

        init {
            applyPlugins(if (useAgp9KmpLine) ANDROID_KMP_LIB_PLUGIN_ID else ANDROID_LIB_PLUGIN_ID)
        }

        override fun setupAndroid(project: Project) {
            if (useAgp9KmpLine) {
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
