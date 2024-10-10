@file:Suppress("LongMethod")

package fluxo.conf.impl.kotlin

import MAIN_SOURCE_SET_POSTFIX
import TEST_SOURCE_SET_POSTFIX
import commonCompileOnly
import commonMain
import commonTest
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

internal fun Project.setupKotlinDependencies(
    dh: DependencyHandler,
    libs: FluxoVersionCatalog?,
    kc: KotlinConfig,
    isApplication: Boolean = false,
) {
    compileOnlyWithConstraint(dh, JSR305_DEPENDENCY)

    if (kc.setupKnownBoms) {
        val bom = dh.kotlin("bom", kc.coreLibs)
        implementation(dh, if (isApplication) dh.enforcedPlatform(bom) else dh.platform(bom))
    }
    if (kc.addStdlibDependency) {
        implementation(dh, dh.kotlin("stdlib", kc.coreLibs))
    }
    testImplementation(dh, dh.kotlin("test-junit"))

    libs ?: return

    libs.onLibrary("jetbrains-annotation") { compileOnlyWithConstraint(dh, it) }

    if (kc.setupCoroutines) {
        kc.setupKnownBoms && libs.onLibrary("kotlinx-coroutines-bom") {
            implementation(
                dh,
                if (isApplication) dh.enforcedPlatform(it) else dh.platform(it),
                excludeJetbrainsAnnotations,
            )
            implementation(dh, COROUTINES_DEPENDENCY)
        } || libs.onLibrary("kotlinx-coroutines-core") { implementation(dh, it) }

        libs.onLibrary("kotlinx-coroutines-test") { testImplementation(dh, it) }
        libs.onLibrary("kotlinx-coroutines-debug") { testImplementation(dh, it) }
    }

    if (!kc.setupKnownBoms) return

    val bomImplementation: (Provider<MinimalExternalModuleDependency>) -> Unit = {
        implementation(
            dh,
            if (isApplication) dh.enforcedPlatform(it) else dh.platform(it),
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

    dh.constraints {
        val constraintImpl: (Any) -> Unit = { implementation(this, it) }

        if (!hasOkioBom) libs.onLibrary("square-okio", constraintImpl)
        if (!hasOkhttpBom) libs.onLibrary("square-okhttp", constraintImpl)

        // TODO: With coil-bom:2.2.2 doesn't work as enforcedPlatform for Android and/or KMP
        libs.onLibrary("coil-bom", constraintImpl)

        libs.onBundle("kotlinx", constraintImpl)
        libs.onBundle("koin", constraintImpl)
        libs.onBundle("common", constraintImpl)
    }
}

@Suppress("CyclomaticComplexMethod")
internal fun Project.setupMultiplatformDependencies(
    kmpe: KotlinMultiplatformExtension,
    conf: FluxoConfigurationExtensionImpl,
    isApplication: Boolean = conf.project.hasAndroidAppPlugin,
) {
    val libs = conf.ctx.libs
    val kc = conf.kotlinConfig
    val project = conf.project
    val dh = project.dependencies
    val sourceSets = kmpe.sourceSets

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
        val jbCompose = (kmpe as ExtensionAware).extensions.findByName("compose")
        if (jbCompose != null && jbCompose is org.jetbrains.compose.ComposePlugin.Dependencies) {
            kmpe.commonCompileOnly(jbCompose.runtime, project)
        }

        // AndroidX Compose
        else {
            libs.onLibrary("androidx-compose-runtime") { lib ->
                sourceSets.register(CommonJvm.ANDROID + MAIN_SOURCE_SET_POSTFIX) {
                    dependencies { compileOnlyAndLog(lib) }
                    implementation(constraints, lib)
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
                implementation(constraints, it)
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
