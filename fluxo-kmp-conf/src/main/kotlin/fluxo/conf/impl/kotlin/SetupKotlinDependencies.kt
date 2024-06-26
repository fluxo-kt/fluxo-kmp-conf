@file:Suppress("LongMethod")

package fluxo.conf.impl.kotlin

import MAIN_SOURCE_SET_POSTFIX
import TEST_SOURCE_SET_POSTFIX
import commonCompileOnly
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.CommonJvm
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.JSR305_DEPENDENCY
import fluxo.conf.impl.android.hasAndroidAppPlugin
import fluxo.conf.impl.compileOnlyAndLog
import fluxo.conf.impl.compileOnlyWithConstraint
import fluxo.conf.impl.implementation
import fluxo.conf.impl.implementationAndLog
import fluxo.conf.impl.kotlin
import fluxo.conf.impl.testImplementation
import fluxo.vc.FluxoVersionCatalog
import fluxo.vc.l
import fluxo.vc.onBundle
import fluxo.vc.onLibrary
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

context(Project)
internal fun DependencyHandler.setupKotlinDependencies(
    libs: FluxoVersionCatalog?,
    kc: KotlinConfig,
    isApplication: Boolean = false,
) {
    compileOnlyWithConstraint(JSR305_DEPENDENCY)

    if (kc.setupKnownBoms) {
        val bom = kotlin("bom", kc.coreLibs)
        implementation(if (isApplication) enforcedPlatform(bom) else platform(bom))
    }
    if (kc.addStdlibDependency) {
        implementation(kotlin("stdlib", kc.coreLibs))
    }
    testImplementation(kotlin("test-junit"))

    libs ?: return

    libs.onLibrary("jetbrains-annotation") { compileOnlyWithConstraint(it) }

    if (kc.setupCoroutines) {
        kc.setupKnownBoms && libs.onLibrary("kotlinx-coroutines-bom") {
            implementation(
                if (isApplication) enforcedPlatform(it) else platform(it),
                excludeJetbrainsAnnotations,
            )
            implementation(COROUTINES_DEPENDENCY)
        } || libs.onLibrary("kotlinx-coroutines-core") { implementation(it) }

        libs.onLibrary("kotlinx-coroutines-test") { testImplementation(it) }
        libs.onLibrary("kotlinx-coroutines-debug") { testImplementation(it) }
    }

    if (!kc.setupKnownBoms) return

    val bomImplementation: (Provider<MinimalExternalModuleDependency>) -> Unit = {
        implementation(
            if (isApplication) enforcedPlatform(it) else platform(it),
            excludeJetbrainsAnnotations,
        )
    }
    if (kc.setupSerialization) {
        libs.onLibrary("kotlinx-serialization-bom", bomImplementation)
    }

    libs.onLibrary("ktor-bom", bomImplementation)
    libs.onLibrary("arrow-bom", bomImplementation)
    val hasOkioBom = libs.onLibrary("square-okio-bom", bomImplementation)
    val hasOkhttpBom = libs.onLibrary("square-okhttp-bom", bomImplementation)

    constraints {
        val constraintImpl: (Any) -> Unit = { implementation(it) }

        if (!hasOkioBom) libs.onLibrary("square-okio", constraintImpl)
        if (!hasOkhttpBom) libs.onLibrary("square-okhttp", constraintImpl)

        // TODO: With coil-bom:2.2.2 doesn't work as enforcedPlatform for Android and/or KMP
        libs.onLibrary("coil-bom", constraintImpl)

        libs.onBundle("kotlinx", constraintImpl)
        libs.onBundle("koin", constraintImpl)
        libs.onBundle("common", constraintImpl)
    }
}

context(Project)
@Suppress("CyclomaticComplexMethod")
internal fun KotlinMultiplatformExtension.setupMultiplatformDependencies(
    conf: FluxoConfigurationExtensionImpl,
    isApplication: Boolean = conf.project.hasAndroidAppPlugin,
) {
    val libs = conf.ctx.libs
    val kc = conf.kotlinConfig
    val project = conf.project
    val dh = project.dependencies
    val sourceSets = sourceSets

    sourceSets.commonMain.dependencies {
        if (kc.setupKnownBoms) {
            val bom = kotlin("bom", kc.coreLibs)
            implementationAndLog(if (isApplication) dh.enforcedPlatform(bom) else dh.platform(bom))

            val bomImplementation: (Provider<MinimalExternalModuleDependency>) -> Unit = {
                val platformNotation = when {
                    isApplication -> dh.enforcedPlatform(it)
                    else -> dh.platform(it)
                }
                implementationAndLog(platformNotation)
            }
            if (kc.setupCoroutines) {
                libs.onLibrary("kotlinx-coroutines-bom", bomImplementation)
            }
            libs.onLibrary("square-okio-bom", bomImplementation)
            libs.onLibrary("square-okhttp-bom", bomImplementation)
            libs.onLibrary("ktor-bom", bomImplementation)
            libs.onLibrary("arrow-bom", bomImplementation)

            if (kc.setupSerialization) {
                libs.onLibrary("kotlinx-serialization-bom", bomImplementation)
            }
        }
        if (kc.addStdlibDependency) {
            implementationAndLog(kotlin("stdlib", kc.coreLibs), excludeJetbrainsAnnotations)
        }
        if (kc.setupCoroutines) {
            libs.onLibrary("kotlinx-coroutines-core") { implementationAndLog(it) }
        }
    }

    sourceSets.commonTest.dependencies {
        implementationAndLog(kotlin("test"))
        implementationAndLog(kotlin("reflect"))

        libs.onLibrary("kotlinx-datetime") { implementationAndLog(it) }

        if (kc.setupCoroutines) {
            libs.onLibrary("kotlinx-coroutines-test") { implementationAndLog(it) }
            libs.onLibrary("test-turbine") { implementationAndLog(it) }
        }
    }

    // Support Compose @Stable and @Immutable annotations
    val constraints = dh.constraints
    if (kc.setupCompose) {
        // Jetbrains KMP Compose
        val jbCompose = (this as ExtensionAware).extensions.findByName("compose")
        if (jbCompose != null && jbCompose is org.jetbrains.compose.ComposePlugin.Dependencies) {
            commonCompileOnly(jbCompose.runtime, project)
        }

        // AndroidX Compose
        else {
            libs.onLibrary("androidx-compose-runtime") { lib ->
                sourceSets.register(CommonJvm.ANDROID + MAIN_SOURCE_SET_POSTFIX) {
                    dependencies { compileOnlyAndLog(lib) }
                    constraints.implementation(lib)
                }
            }
        }
    }

    sourceSets.register(CommonJvm.COMMON_JVM + MAIN_SOURCE_SET_POSTFIX) {
        dependencies {
            // TODO: Use `compileOnlyApi` for transitively included compile-only dependencies.
            // https://issuetracker.google.com/issues/216305675
            // https://issuetracker.google.com/issues/216293107
            val compileOnlyWithConstraint: (Any) -> Unit = {
                compileOnlyAndLog(it)
                constraints.implementation(it)
            }

            // Java-only annotations
            libs.onLibrary("jetbrains-annotation", compileOnlyWithConstraint)
            compileOnlyWithConstraint(JSR305_DEPENDENCY)

            // Note: not usable for the common source set as supports only a KMM set of targets!
            // https://issuetracker.google.com/issues/273468771
            libs.onLibrary("androidx-annotation") { compileOnlyWithConstraint(it) }
        }
    }
    sourceSets.register(CommonJvm.COMMON_JVM + TEST_SOURCE_SET_POSTFIX) {
        dependencies {
            // Help with https://youtrack.jetbrains.com/issue/KT-29341
            val junit = libs.l("junit", "test-junit") ?: JUNIT_DEPENDENCY
            compileOnlyAndLog(junit)
        }
    }
}
