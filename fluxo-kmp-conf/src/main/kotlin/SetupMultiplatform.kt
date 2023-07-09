@file:Suppress("TooManyFunctions")

import com.android.build.gradle.LibraryExtension
import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.capitalizeAsciiOnly
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.getOrNull
import fluxo.conf.impl.ifNotEmpty
import fluxo.conf.impl.implementation
import fluxo.conf.impl.isTestRelated
import fluxo.conf.impl.kotlin
import fluxo.conf.impl.libsCatalog
import fluxo.conf.impl.onLibrary
import fluxo.conf.impl.testImplementation
import fluxo.conf.impl.v
import fluxo.conf.impl.withType
import fluxo.conf.kmp.SourceSetBundle
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer as NDOC
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_TEST_SOURCE_SET_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetsContainer
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultKotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl
import org.jetbrains.kotlin.konan.target.Family

@Deprecated("")
public typealias MultiplatformConfigurator = KotlinMultiplatformExtension.() -> Unit

internal val Project.multiplatformExtension: KotlinMultiplatformExtension
    get() = kotlinExtension as KotlinMultiplatformExtension

@Suppress("LongParameterList")
public fun Project.setupMultiplatform(
    config: KotlinConfigSetup = requireDefaultKotlinConfigSetup(),
    namespace: String? = null,
    setupCompose: Boolean = false,
    enableBuildConfig: Boolean? = null,
    optIns: List<String> = emptyList(),
    configurator: MultiplatformConfigurator? = getDefaults(),
    configureAndroid: (LibraryExtension.() -> Unit)? = null,
    body: MultiplatformConfigurator? = null,
) {
    multiplatformExtension.apply {
        logger.lifecycle("> Conf :setupMultiplatform")

        setupKotlinExtension(kotlin = this, config = config, optIns = optIns)
        setupMultiplatformDependencies(config, project)

        configurator?.invoke(this)

        setupSourceSets(project)

        val jbCompose = (this as ExtensionAware).extensions.findByName("compose")
        val setupAndroidCompose = setupCompose && jbCompose == null
        if (isMultiplatformTargetEnabled(Target.ANDROID) || configureAndroid != null) {
            setupAndroidCommon(
                namespace = checkNotNull(namespace) { "namespace is required for android setup" },
                setupKsp = false,
                setupRoom = false,
                setupCompose = setupAndroidCompose,
                enableBuildConfig = enableBuildConfig,
                kotlinConfig = config,
            )
            project.configureExtension<LibraryExtension>("android") {
                configureAndroid?.invoke(this)
            }
        }

        if (setupCompose) {
            setupCompose(project, jbCompose)
        }

        body?.invoke(this)

        disableCompilationsOfNeeded(project)
    }
}

private fun KotlinMultiplatformExtension.setupMultiplatformDependencies(
    config: KotlinConfigSetup,
    project: Project,
) {
    setupSourceSets {
        val libs = project.libsCatalog

        val kotlinVersion = libs.v("kotlin")
        project.dependencies.apply {
            enforcedPlatform(kotlin("bom", kotlinVersion)).let {
                if (config.setupKnownBoms) implementation(it) else testImplementation(it)
            }

            if (config.setupCoroutines) {
                libs.onLibrary("kotlinx-coroutines-bom") {
                    val platform = enforcedPlatform(it)
                    if (config.setupKnownBoms) {
                        implementation(platform)
                    } else {
                        testImplementation(platform)
                    }
                }
            }

            val platformImplementation: (Provider<MinimalExternalModuleDependency>) -> Unit = {
                val platform = platform(it)
                if (config.setupKnownBoms) {
                    implementation(platform)
                } else {
                    testImplementation(platform)
                }
            }
            libs.onLibrary("square-okio-bom", platformImplementation)
            libs.onLibrary("square-okhttp-bom", platformImplementation)
        }

        common.main.dependencies {
            if (config.addStdlibDependency) {
                implementation(kotlin("stdlib", kotlinVersion), excludeAnnotations)
            }

            if (config.setupCoroutines) {
                libs.onLibrary("kotlinx-coroutines-core") { implementation(it) }
            }
        }

        common.test.dependencies {
            implementation(kotlin("reflect"))
            implementation(kotlin("test"))

            libs.onLibrary("kotlinx-datetime") { implementation(it) }

            if (config.setupCoroutines) {
                libs.onLibrary("kotlinx-coroutines-test") { implementation(it) }
                libs.onLibrary("test-turbine") { implementation(it) }
            }
        }
    }
}

private fun KotlinMultiplatformExtension.setupSourceSets(project: Project) {
    setupSourceSets {
        setupCommonJavaSourceSets(project, javaSet)

        val nativeSet = nativeSet.toMutableSet()
        val jsSet = jsSet
        if (nativeSet.isNotEmpty() || jsSet.isNotEmpty()) {
            val nativeAndJs by bundle()
            nativeAndJs dependsOn common

            val native by bundle()
            native dependsOn nativeAndJs

            if (jsSet.isNotEmpty()) {
                val js by bundle()
                js dependsOn nativeAndJs
                native dependsOn common
            }

            if (nativeSet.isNotEmpty()) {
                darwinSet.ifNotEmpty {
                    nativeSet -= this
                    val apple by bundle()
                    apple dependsOn native
                    this dependsOn apple
                }

                linuxSet.ifNotEmpty {
                    nativeSet -= this
                    val linux by bundle()
                    linux dependsOn native
                    this dependsOn linux
                }

                mingwSet.ifNotEmpty {
                    nativeSet -= this
                    val mingw by bundle()
                    mingw dependsOn native
                    this dependsOn mingw
                }

                nativeSet dependsOn native
            }
        }
    }
}

private fun MultiplatformSourceSets.setupCommonJavaSourceSets(
    project: Project,
    sourceSet: Set<SourceSetBundle>,
) {
    if (sourceSet.isEmpty()) {
        return
    }

    val javaCommon = bundle("java")
    javaCommon dependsOn common
    sourceSet dependsOn javaCommon

    val libs = project.libsCatalog
    val constraints = project.dependencies.constraints
    (sourceSet + javaCommon).main.dependencies {
        val compileOnlyWithConstraint: (Any) -> Unit = {
            compileOnly(it)
            constraints.implementation(it)
        }

        // Java-only annotations
        libs.onLibrary("jetbrains-annotation", compileOnlyWithConstraint)
        compileOnlyWithConstraint(JSR305_DEPENDENCY)

        // TODO: Use `compileOnlyApi` for transitively included compile-only dependencies.
        // https://issuetracker.google.com/issues/216293107
        // https://issuetracker.google.com/issues/216305675
        //
        // Also note that atm androidx annotations aren't usable in the common source sets!
        // https://issuetracker.google.com/issues/273468771
        libs.onLibrary("androidx-annotation") { compileOnlyWithConstraint(it) }
    }

    // Help with https://youtrack.jetbrains.com/issue/KT-29341
    javaCommon.test.dependencies {
        val junit = libs.findLibrary("test-junit")
            .or { libs.findLibrary("junit") }
        compileOnly(junit.getOrNull() ?: "junit:junit:4.13.2")
    }
}

private fun KotlinMultiplatformExtension.setupCompose(project: Project, jbCompose: Any?) {
    setupSourceSets {
        val libs = project.libsCatalog

        // AndroidX Compose
        if (jbCompose == null) {
            val constraints = project.dependencies.constraints
            androidSet.main.dependencies {
                // Support compose @Stable and @Immutable annotations
                libs.onLibrary("androidx-compose-runtime") {
                    compileOnly(it)
                    constraints.implementation(it)
                }
            }
        }

        // Jetbrains KMP Compose
        else {
            // Support compose @Stable and @Immutable annotations
            val composeDependency = jbCompose as org.jetbrains.compose.ComposePlugin.Dependencies
            commonCompileOnly(composeDependency.runtime)
        }
    }
}


/**
 *
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts
 */
public fun KotlinMultiplatformExtension.setupSourceSets(block: MultiplatformSourceSets.() -> Unit) {
    MultiplatformSourceSets(targets, sourceSets).block()
}

/**
 * Configure a separate Kotlin/Native tests where code runs in worker thread.
 */
public fun KotlinMultiplatformExtension.setupBackgroundNativeTests() {
    // Configure a separate test where code runs in worker thread
    // https://kotlinlang.org/docs/compiler-reference.html#generate-worker-test-runner-trw
    targets.withType<KotlinNativeTargetWithTests<*>>().all {
        val background = "background"
        binaries {
            test(background, listOf(DEBUG)) {
                freeCompilerArgs += "-trw"
            }
        }
        testRuns.create(background) {
            setExecutionSourceFrom(binaries.getTest(background, DEBUG))
        }
    }
}

/**
 *
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension.targetHierarchy
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetHierarchyDsl.default
 */
@SinceKotlin("1.8.20")
@Suppress("NEWER_VERSION_IN_SINCE_KOTLIN")
@Deprecated("Fails with NoClassDefFoundError: org/jetbrains/kotlin/gradle/plugin/KotlinTargetHierarchyBuilder\$Root")
@ExperimentalKotlinGradlePluginApi
public fun KotlinMultiplatformExtension.setupTargetHierarchy() {
    // Fails with NoClassDefFoundError:
    //    org/jetbrains/kotlin/gradle/plugin/KotlinTargetHierarchyBuilder$Root
    //  when called in plugin code!

    // https://kotlinlang.org/docs/multiplatform-hierarchy.html#default-hierarchy
    targetHierarchy.default {
        group("commonJvm") {
            withJvm()
            // FIXME: withAndroidTarget
            //  https://kotl.in/android-target-dsl
            withAndroid()
        }
        group("nonJvm") {
            group("commonJs") {
                withCompilations { it.target.platformType == KotlinPlatformType.wasm }
                withJs()
            }
            group("native") {
                group("unix") {
                    withLinux()
                    withApple()
                }
                @Suppress("DEPRECATION")
                group("wasmNative") {
                    withWasm32()
                }
            }
        }
    }
}

internal enum class Target {
    ANDROID,
    JVM,
    JS,
}

internal fun Project.isMultiplatformTargetEnabled(target: Target): Boolean =
    multiplatformExtension.isMultiplatformTargetEnabled(target)

internal fun KotlinTargetsContainer.isMultiplatformTargetEnabled(target: Target): Boolean =
    targets.any {
        when (it.platformType) {
            KotlinPlatformType.androidJvm -> target == Target.ANDROID
            KotlinPlatformType.jvm -> target == Target.JVM
            KotlinPlatformType.js -> target == Target.JS
            KotlinPlatformType.common,
            KotlinPlatformType.native,
            KotlinPlatformType.wasm,
            -> false
        }
    }


// TODO: Split to KotlinMultiplatformExtension extensions?
public class MultiplatformSourceSets
internal constructor(
    private val targets: NamedDomainObjectCollection<KotlinTarget>,
    private val sourceSets: NDOC<KotlinSourceSet>,
) : NDOC<KotlinSourceSet> by sourceSets {

    /** All enabled targets */
    public val allSet: Set<SourceSetBundle> get() = targets.toSourceSetBundles()

    /** androidJvm, jvm */
    public val javaSet: Set<SourceSetBundle>
        get() = targets.matching {
            when (it.platformType) {
                KotlinPlatformType.androidJvm,
                KotlinPlatformType.jvm,
                -> true

                else -> false
            }
        }.toSourceSetBundles()

    /** androidJvm */
    public val androidSet: Set<SourceSetBundle>
        get() = targets.matching { it.platformType == KotlinPlatformType.androidJvm }
            .toSourceSetBundles()

    /** js */
    public val jsSet: Set<SourceSetBundle>
        get() = targets.matching { it.platformType == KotlinPlatformType.js }
            .toSourceSetBundles()

    /** All Kotlin/Native targets */
    public val nativeSet: Set<SourceSetBundle> get() = nativeSourceSets()
    public val linuxSet: Set<SourceSetBundle> get() = nativeSourceSets(Family.LINUX)
    public val mingwSet: Set<SourceSetBundle> get() = nativeSourceSets(Family.MINGW)
    public val androidNativeSet: Set<SourceSetBundle> get() = nativeSourceSets(Family.ANDROID)
    public val wasmSet: Set<SourceSetBundle> get() = nativeSourceSets(Family.WASM)

    /** All Darwin targets */
    public val darwinSet: Set<SourceSetBundle>
        get() = nativeSourceSets(Family.IOS, Family.OSX, Family.WATCHOS, Family.TVOS)
    public val iosSet: Set<SourceSetBundle> get() = nativeSourceSets(Family.IOS)
    public val watchosSet: Set<SourceSetBundle> get() = nativeSourceSets(Family.WATCHOS)
    public val tvosSet: Set<SourceSetBundle> get() = nativeSourceSets(Family.TVOS)
    public val macosSet: Set<SourceSetBundle> get() = nativeSourceSets(Family.OSX)

    private fun nativeSourceSets(vararg families: Family = Family.values()): Set<SourceSetBundle> =
        targets.filter { it is KotlinNativeTarget && it.konanTarget.family in families }
            .toSourceSetBundles()

    private fun Iterable<KotlinTarget>.toSourceSetBundles(): Set<SourceSetBundle> {
        return mapNotNullTo(LinkedHashSet()) {
            when (it.platformType) {
                KotlinPlatformType.common -> null
                else -> bundleFor(it)
            }
        }
    }


    // TODO: Make it available for KotlinMultiplatformExtension
    public fun commonCompileOnly(dependencyNotation: Any) {
        // A compileOnly dependencies aren't applicable for Kotlin/Native.
        // Use 'implementation' or 'api' dependency type instead.
        common.main.dependencies {
            compileOnly(dependencyNotation)
        }

        // FIXME: Set in the shared "native" target instead
        nativeSet.main.dependencies {
            implementation(dependencyNotation)
        }
    }
}


public val NamedDomainObjectCollection<out KotlinSourceSet>.common: SourceSetBundle
    get() = SourceSetBundle(
        getByName(COMMON_MAIN_SOURCE_SET_NAME),
        getByName(COMMON_TEST_SOURCE_SET_NAME),
    )

internal fun KotlinProjectExtension.bundleFor(target: KotlinTarget) = sourceSets.bundleFor(target)

internal fun NDOC<out KotlinSourceSet>.bundleFor(
    target: KotlinTarget,
    androidLayoutV2: Boolean? = null,
): SourceSetBundle {
    val compilations = target.compilations
    return when {
        compilations.isEmpty() || androidLayoutV2 != null ->
            bundle(target.name, androidLayoutV2 = androidLayoutV2)

        else -> SourceSetBundle(
            main = compilations.getByName(MAIN_SOURCE_SET_NAME).defaultSourceSet,
            test = compilations.getByName(TEST_SOURCE_SET_NAME).defaultSourceSet,
        )
    }
}

public fun NDOC<out KotlinSourceSet>.bundle(
    name: String,
    androidLayoutV2: Boolean? = null,
): SourceSetBundle {
    val mainSourceSet = maybeCreate("${name}Main")

    // region Support for androidSourceSetLayout v2
    // https://kotlinlang.org/docs/whatsnew18.html#kotlinsourceset-naming-schema
    /** @see fluxo.conf.dsl.container.impl.target.TargetAndroidContainer.setup */
    val isAndroid = name == "android"
    if (isAndroid) {
        val useV1 = androidLayoutV2?.not()
            ?: names.let { "androidAndroidTest" in it || "androidTest" in it }
        val instrumentedTest =
            maybeCreate(if (!useV1) "androidInstrumentedTest" else "androidAndroidTest")
        return SourceSetBundle(
            main = mainSourceSet,
            test = maybeCreate(if (!useV1) "androidUnitTest" else "androidTest"),
            otherTests = arrayOf(instrumentedTest),
        )
    }
    // endregion

    return SourceSetBundle(
        main = mainSourceSet,
        test = maybeCreate("${name}Test"),
    )
}

/**
 *
 * @see kotlin.properties.PropertyDelegateProvider
 * @see kotlin.properties.ReadOnlyProperty
 */
public fun NDOC<out KotlinSourceSet>.bundle(
    name: String? = null,
): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, SourceSetBundle>> =
    PropertyDelegateProvider { _, property ->
        val bundle = bundle(name = name ?: property.name)
        ReadOnlyProperty { _, _ -> bundle }
    }

internal const val MAIN_SOURCE_SET_NAME = "main"
internal const val TEST_SOURCE_SET_NAME = "test"
internal const val MAIN_SOURCE_SET_POSTFIX = "Main"


// region Kotlin/JS convenience

internal val DEFAULT_COMMON_JS_CONFIGURATION: KotlinTargetContainer<KotlinJsTargetDsl>.() -> Unit =
    {
        target {
            defaults()
        }
    }


public fun KotlinJsTargetDsl.defaults() {
    testTimeout()

    if (this is KotlinWasmTargetDsl) {
        applyBinaryen()
    }

    compilations.all {
        kotlinOptions {
            moduleKind = "es"
            useEsClasses = true
            sourceMap = true
            metaInfo = true
        }
    }

    // Generate TypeScript declaration files
    // https://kotlinlang.org/docs/js-ir-compiler.html#preview-generation-of-typescript-declaration-files-d-ts
    binaries.executable()
    generateTypeScriptDefinitions()
}


public fun KotlinJsTargetDsl.testTimeout(seconds: Int = TEST_TIMEOUT) {
    browser {
        testTimeout(seconds)
    }
    nodejs {
        testTimeout(seconds)
    }
    if (this is KotlinWasmTargetDsl) {
        d8 {
            testTimeout(seconds)
        }
    }
}

public fun KotlinJsSubTargetDsl.testTimeout(seconds: Int = TEST_TIMEOUT) {
    require(seconds > 0) { "Timeout seconds must be greater than 0." }
    testTask {
        useMocha { timeout = "${seconds}s" }
    }
}

/**
 * Default timeout for Kotlin/JS tests is `2s`.
 *
 * @see org.jetbrains.kotlin.gradle.targets.js.testing.mocha.KotlinMocha.DEFAULT_TIMEOUT
 */
// https://mochajs.org/#-timeout-ms-t-ms
private const val TEST_TIMEOUT = 10

// endregion


// region Dependecies declaration

public operator fun SourceSetBundle.plus(other: SourceSetBundle): Set<SourceSetBundle> =
    this + setOf(other)

public operator fun SourceSetBundle.plus(other: Set<SourceSetBundle>): Set<SourceSetBundle> =
    setOf(this) + other

public infix fun SourceSetBundle.dependsOn(other: SourceSetBundle) {
    main.dependsOn(other.main)
    test.dependsOn(other.test)
    otherTests?.forEach {
        it.dependsOn(other.test)
    }
}

public infix fun Iterable<SourceSetBundle>.dependsOn(other: Iterable<SourceSetBundle>) {
    forEach { left ->
        other.forEach { right ->
            left.dependsOn(right)
        }
    }
}

public infix fun SourceSetBundle.dependsOn(other: Iterable<SourceSetBundle>) {
    listOf(this) dependsOn other
}

public infix fun Iterable<SourceSetBundle>.dependsOn(other: SourceSetBundle) {
    this dependsOn listOf(other)
}


public infix fun KotlinSourceSet.dependsOn(other: SourceSetBundle) {
    dependsOn(if (isTestRelated()) other.test else other.main)
}

@JvmName("dependsOnBundles")
public infix fun KotlinSourceSet.dependsOn(other: Iterable<SourceSetBundle>) {
    other.forEach { right ->
        dependsOn(right)
    }
}

public infix fun KotlinSourceSet.dependsOn(other: Iterable<KotlinSourceSet>) {
    other.forEach { right ->
        dependsOn(right)
    }
}


public val Iterable<SourceSetBundle>.main: List<KotlinSourceSet> get() = map { it.main }

public val Iterable<SourceSetBundle>.test: List<KotlinSourceSet> get() = map { it.test }

public infix fun Iterable<KotlinSourceSet>.dependencies(
    configure: KotlinDependencyHandler.() -> Unit,
) {
    forEach { left ->
        left.dependencies(configure)
    }
}


public fun KotlinDependencyHandler.ksp(dependencyNotation: Any): Dependency? {
    // Starting from KSP 1.0.1, applying KSP on a multiplatform project requires
    // instead of writing the ksp("dep")
    // use ksp<Target>() or add(ksp<SourceSet>).
    // https://kotlinlang.org/docs/ksp-multiplatform.html
    val parent = (this as DefaultKotlinDependencyHandler).parent
    var configurationName =
        parent.compileOnlyConfigurationName.replace("compileOnly", "", ignoreCase = true)
    if (configurationName.startsWith(COMMON_MAIN_SOURCE_SET_NAME, ignoreCase = true)) {
        configurationName += "Metadata"
    } else {
        configurationName =
            configurationName.replace(MAIN_SOURCE_SET_POSTFIX, "", ignoreCase = true)
    }
    configurationName = "ksp${configurationName.capitalizeAsciiOnly()}"
    project.logger.lifecycle(">>> ksp configurationName: $configurationName")
    return project.dependencies.add(configurationName, dependencyNotation)
}

// endregion


// region Darwin compat

/**
 *
 * @see KotlinTargetContainerWithNativeShortcuts.ios
 * @see KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
 */
public fun KotlinMultiplatformExtension.iosCompat(
    x64: String? = DEFAULT_TARGET_NAME,
    arm64: String? = DEFAULT_TARGET_NAME,
    simulatorArm64: String? = DEFAULT_TARGET_NAME,
) {
    enableTarget(name = x64, enableDefault = { iosX64() }, enableNamed = { iosX64(it) })
    enableTarget(name = arm64, enableDefault = { iosArm64() }, enableNamed = { iosArm64(it) })
    enableTarget(
        name = simulatorArm64,
        enableDefault = { iosSimulatorArm64() },
        enableNamed = { iosSimulatorArm64(it) },
    )
}

/**
 *
 * @see KotlinTargetContainerWithNativeShortcuts.watchos
 * @see KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
 */
public fun KotlinMultiplatformExtension.watchosCompat(
    x64: String? = DEFAULT_TARGET_NAME,
    arm32: String? = DEFAULT_TARGET_NAME,
    arm64: String? = DEFAULT_TARGET_NAME,
    simulatorArm64: String? = DEFAULT_TARGET_NAME,
) {
    enableTarget(name = x64, enableDefault = { watchosX64() }, enableNamed = { watchosX64(it) })
    enableTarget(
        name = arm32,
        enableDefault = { watchosArm32() },
        enableNamed = { watchosArm32(it) },
    )
    enableTarget(
        name = arm64,
        enableDefault = { watchosArm64() },
        enableNamed = { watchosArm64(it) },
    )
    enableTarget(
        name = simulatorArm64,
        enableDefault = { watchosSimulatorArm64() },
        enableNamed = { watchosSimulatorArm64(it) },
    )
}

/**
 *
 * @see KotlinTargetContainerWithNativeShortcuts.tvos
 * @see KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
 */
public fun KotlinMultiplatformExtension.tvosCompat(
    x64: String? = DEFAULT_TARGET_NAME,
    arm64: String? = DEFAULT_TARGET_NAME,
    simulatorArm64: String? = DEFAULT_TARGET_NAME,
) {
    enableTarget(name = x64, enableDefault = { tvosX64() }, enableNamed = { tvosX64(it) })
    enableTarget(name = arm64, enableDefault = { tvosArm64() }, enableNamed = { tvosArm64(it) })
    enableTarget(
        name = simulatorArm64,
        enableDefault = { tvosSimulatorArm64() },
        enableNamed = { tvosSimulatorArm64(it) },
    )
}

/**
 *
 * @see KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
 */
public fun KotlinMultiplatformExtension.macosCompat(
    x64: String? = DEFAULT_TARGET_NAME,
    arm64: String? = DEFAULT_TARGET_NAME,
) {
    enableTarget(name = x64, enableDefault = { macosX64() }, enableNamed = { macosX64(it) })
    enableTarget(name = arm64, enableDefault = { macosArm64() }, enableNamed = { macosArm64(it) })
}

private fun KotlinMultiplatformExtension.enableTarget(
    name: String?,
    enableDefault: KotlinMultiplatformExtension.() -> Unit,
    enableNamed: KotlinMultiplatformExtension.(String) -> Unit,
) {
    if (name != null) {
        if (name == DEFAULT_TARGET_NAME) {
            enableDefault()
        } else {
            enableNamed(name)
        }
    }
}

private const val DEFAULT_TARGET_NAME = ".DEFAULT_TARGET_NAME"

// endregion
