package fluxo.conf.impl.android

import fluxo.conf.impl.androidTestImplementation
import fluxo.conf.impl.compileOnlyWithConstraint
import fluxo.conf.impl.debugCompileOnly
import fluxo.conf.impl.debugImplementation
import fluxo.conf.impl.exclude
import fluxo.conf.impl.implementation
import fluxo.conf.impl.kotlin
import fluxo.conf.impl.kotlin.KotlinConfig
import fluxo.conf.impl.ksp
import fluxo.conf.impl.onBundle
import fluxo.conf.impl.onLibrary
import fluxo.conf.impl.runtimeOnly
import fluxo.conf.impl.testImplementation
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.dsl.DependencyHandler

@Suppress("LongMethod")
internal fun DependencyHandler.setupAndroidDependencies(
    libs: VersionCatalog?,
    isApplication: Boolean,
    kc: KotlinConfig,
) {
    androidTestImplementation(kotlin("test-junit", kc.coreLibs))

    libs ?: return

    val impl: (Any) -> Unit = { implementation(it) }
    val compileConstraint: (Any) -> Unit = { compileOnlyWithConstraint(it) }
    val debugImpl: (Any) -> Unit = { debugImplementation(it) }
    val testImpl: (Any) -> Unit = { testImplementation(it) }
    val androidTestImpl: (Any) -> Unit = { androidTestImplementation(it) }

    libs.onLibrary("androidx-annotation", compileConstraint)
    libs.onLibrary("androidx-annotation-experimental", compileConstraint)

    if (kc.setupCoroutines) {
        libs.onLibrary("kotlinx-coroutines-debug") { debugCompileOnly(it) }
        libs.onLibrary("kotlinx-coroutines-test") {
            androidTestImplementation(it) {
                // https://github.com/Kotlin/kotlinx.coroutines/tree/ca14606/kotlinx-coroutines-debug#debug-agent-and-android
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-debug")
            }
        }
    }

    if (kc.setupCompose) {
        // Support compose @Stable and @Immutable annotations
        libs.onLibrary("androidx-compose-runtime", compileConstraint)
        // Support compose @Preview
        libs.onLibrary("androidx-compose-ui-tooling-preview", compileConstraint)
        // Support Layout inspector and any other tools
        libs.onLibrary("androidx-compose-ui-tooling", debugImpl)
        // Experimental composition tracing
        // https://developer.android.com/jetpack/compose/performance/tracing
        libs.onLibrary("androidx-compose-tracing", debugImpl)
        // Tests
        libs.onLibrary("androidx-compose-ui-test", androidTestImpl)
        libs.onLibrary("androidx-compose-ui-test-junit4", androidTestImpl)
        libs.onLibrary("androidx-compose-ui-test-manifest", androidTestImpl)
    }

    if (isApplication) {
        libs.onLibrary("androidx-activity", impl)
        libs.onLibrary("androidx-lifecycle-runtime", impl)
        libs.onLibrary("androidx-profileInstaller") { runtimeOnly(it) }

        if (kc.setupCompose) {
            // BackHandler, setContent, ReportDrawn, rememberLauncherForActivityResult, and so on.
            libs.onLibrary("androidx-activity-compose", impl)
        }

        libs.onLibrary(ALIAS_LEAK_CANARY, debugImpl)
        libs.onLibrary("square-plumber", impl)

        libs.onLibrary("flipper") { flipper ->
            debugImpl(flipper)
            libs.onLibrary("flipper-leakcanary2", debugImpl)
            libs.onLibrary("flipper-network", debugImpl)
            libs.onLibrary("flipper-soloader", debugImpl)
        }
    }

    libs.onLibrary("test-jUnit", testImpl) || libs.onLibrary("junit", testImpl)
    libs.onLibrary("test-mockito-core", testImpl)
    libs.onLibrary("test-robolectric", testImpl)

    libs.onLibrary("androidx-arch-core-testing", testImpl)
    libs.onLibrary("androidx-test-core", testImpl)
    libs.onLibrary("androidx-test-core-ktx", testImpl)
    libs.onLibrary("androidx-test-junit", testImpl)

    libs.onLibrary("androidx-test-core", androidTestImpl)
    libs.onLibrary("androidx-test-core-ktx", androidTestImpl)
    libs.onLibrary("androidx-test-espresso-core", androidTestImpl)
    libs.onLibrary("androidx-test-espresso-idling", androidTestImpl)
    libs.onLibrary("androidx-test-junit", androidTestImpl)
    libs.onLibrary("androidx-test-rules", androidTestImpl)
    libs.onLibrary("androidx-test-runner", androidTestImpl)

    if (kc.setupKnownBoms) {
        libs.onLibrary("firebase-bom") {
            implementation(if (isApplication) enforcedPlatform(it) else platform(it))
        }
        libs.onLibrary("androidx-compose-bom") { implementation(platform(it)) }
    }

    if (kc.setupRoom) {
        libs.onLibrary("androidx-room-compiler") { ksp(it) }
        libs.onLibrary("androidx-room-runtime", impl)
        libs.onLibrary("androidx-room-testing", testImpl)
        libs.onLibrary("androidx-room-common", compileConstraint)

        libs.onLibrary("androidx-room-paging", impl)

        if (kc.setupCoroutines) {
            libs.onLibrary("androidx-room-ktx", impl)
        }
    }

    if (!kc.setupKnownBoms) return

    constraints {
        val constraintImpl: (Any) -> Unit = { implementation(it) }
        libs.onBundle("androidx", constraintImpl)
        libs.onBundle("accompanist", constraintImpl)
        libs.onBundle("android-common", constraintImpl)
        libs.onBundle("gms", constraintImpl)
    }
}
