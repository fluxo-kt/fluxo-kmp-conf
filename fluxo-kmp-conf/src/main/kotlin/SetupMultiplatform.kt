@file:Suppress("TooManyFunctions")

import com.android.build.gradle.LibraryExtension
import impl.capitalizeAsciiOnly
import impl.configureExtension
import impl.getOrNull
import impl.ifNotEmpty
import impl.implementation
import impl.kotlin
import impl.libsCatalog
import impl.onLibrary
import impl.optionalVersion
import impl.testImplementation
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetsContainer
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultKotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

public typealias MultiplatformConfigurator = KotlinMultiplatformExtension.() -> Unit

internal val Project.multiplatformExtension: KotlinMultiplatformExtension
    get() = kotlinExtension as KotlinMultiplatformExtension

// FIXME: Improve setup, completely remove disabled sources/plugins/tasks. Add everything dynamically, see:
//  https://github.com/05nelsonm/gradle-kmp-configuration-plugin

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

        val kotlinVersion = libs.optionalVersion("kotlin")
        project.dependencies.apply {
            enforcedPlatform(kotlin("bom", kotlinVersion)).let {
                if (config.allowGradlePlatform) implementation(it) else testImplementation(it)
            }

            if (config.setupCoroutines) {
                libs.onLibrary("kotlinx-coroutines-bom") {
                    val platform = enforcedPlatform(it)
                    if (config.allowGradlePlatform) {
                        implementation(platform)
                    } else {
                        testImplementation(platform)
                    }
                }
            }

            val platformImplementation: (Provider<MinimalExternalModuleDependency>) -> Unit = {
                val platform = platform(it)
                if (config.allowGradlePlatform) {
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


public fun KotlinMultiplatformExtension.setupSourceSets(block: MultiplatformSourceSets.() -> Unit) {
    MultiplatformSourceSets(targets, sourceSets).block()
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

public class MultiplatformSourceSets
internal constructor(
    private val targets: NamedDomainObjectCollection<KotlinTarget>,
    private val sourceSets: NamedDomainObjectContainer<KotlinSourceSet>,
) : NamedDomainObjectContainer<KotlinSourceSet> by sourceSets {

    public val common: SourceSetBundle by bundle()

    /** All enabled targets */
    public val allSet: Set<SourceSetBundle> = targets.toSourceSetBundles()

    /** androidJvm, jvm */
    public val javaSet: Set<SourceSetBundle> = targets
        .filter { it.platformType in setOf(KotlinPlatformType.androidJvm, KotlinPlatformType.jvm) }
        .toSourceSetBundles()

    /** androidJvm */
    public val androidSet: Set<SourceSetBundle> = targets
        .filter { it.platformType == KotlinPlatformType.androidJvm }
        .toSourceSetBundles()

    /** js */
    public val jsSet: Set<SourceSetBundle> = targets
        .filter { it.platformType == KotlinPlatformType.js }
        .toSourceSetBundles()

    /** All Kotlin/Native targets */
    public val nativeSet: Set<SourceSetBundle> = nativeSourceSets()
    public val linuxSet: Set<SourceSetBundle> = nativeSourceSets(Family.LINUX)
    public val mingwSet: Set<SourceSetBundle> = nativeSourceSets(Family.MINGW)
    public val androidNativeSet: Set<SourceSetBundle> = nativeSourceSets(Family.ANDROID)
    public val wasmSet: Set<SourceSetBundle> = nativeSourceSets(Family.WASM)

    /** All Darwin targets */
    public val darwinSet: Set<SourceSetBundle> =
        nativeSourceSets(Family.IOS, Family.OSX, Family.WATCHOS, Family.TVOS)
    public val iosSet: Set<SourceSetBundle> = nativeSourceSets(Family.IOS)
    public val watchosSet: Set<SourceSetBundle> = nativeSourceSets(Family.WATCHOS)
    public val tvosSet: Set<SourceSetBundle> = nativeSourceSets(Family.TVOS)
    public val macosSet: Set<SourceSetBundle> = nativeSourceSets(Family.OSX)

    private fun nativeSourceSets(vararg families: Family = Family.values()): Set<SourceSetBundle> =
        targets.filterIsInstance<KotlinNativeTarget>()
            .filter { it.konanTarget.family in families }
            .toSourceSetBundles()

    private fun Iterable<KotlinTarget>.toSourceSetBundles(): Set<SourceSetBundle> {
        return filter { it.platformType != KotlinPlatformType.common }
            .map { it.getSourceSetBundle() }
            .toSet()
    }

    private fun KotlinTarget.getSourceSetBundle(): SourceSetBundle = if (compilations.isEmpty()) {
        bundle(name)
    } else {
        SourceSetBundle(
            main = compilations.getByName("main").defaultSourceSet,
            test = compilations.getByName("test").defaultSourceSet,
        )
    }


    public fun commonCompileOnly(dependencyNotation: Any) {
        // A compileOnly dependencies aren't applicable for Kotlin/Native.
        // Use 'implementation' or 'api' dependency type instead.
        common.main.dependencies {
            compileOnly(dependencyNotation)
        }
        nativeSet.main.dependencies {
            implementation(dependencyNotation)
        }
    }
}

public fun NamedDomainObjectContainer<out KotlinSourceSet>.bundle(name: String): SourceSetBundle {
    return SourceSetBundle(
        main = maybeCreate("${name}Main"),
        // Support for androidSourceSetLayout v2
        // https://kotlinlang.org/docs/whatsnew18.html#kotlinsourceset-naming-schema
        test = maybeCreate(if (name == "android") "${name}UnitTest" else "${name}Test"),
    )
}

public fun NamedDomainObjectContainer<out KotlinSourceSet>.bundle(
    name: String? = null,
): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, SourceSetBundle>> =
    PropertyDelegateProvider { _, property ->
        val bundle = bundle(name = name ?: property.name)
        ReadOnlyProperty { _, _ -> bundle }
    }

public data class SourceSetBundle(
    val main: KotlinSourceSet,
    val test: KotlinSourceSet,
)


// region Dependecies declaration

public operator fun SourceSetBundle.plus(other: SourceSetBundle): Set<SourceSetBundle> =
    this + setOf(other)

public operator fun SourceSetBundle.plus(other: Set<SourceSetBundle>): Set<SourceSetBundle> =
    setOf(this) + other

public infix fun SourceSetBundle.dependsOn(other: SourceSetBundle) {
    main.dependsOn(other.main)
    test.dependsOn(other.test)
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
    dependsOn(if ("Test" in name) other.test else other.main)
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

public infix fun Iterable<KotlinSourceSet>.dependencies(configure: KotlinDependencyHandler.() -> Unit) {
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
    if (configurationName.startsWith("commonMain", ignoreCase = true)) {
        configurationName += "Metadata"
    } else {
        configurationName = configurationName.replace("Main", "", ignoreCase = true)
    }
    configurationName = "ksp${configurationName.capitalizeAsciiOnly()}"
    project.logger.lifecycle(">>> ksp configurationName: $configurationName")
    return project.dependencies.add(configurationName, dependencyNotation)
}

// endregion


// region Darwin compat

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

private const val DEFAULT_TARGET_NAME = "fluxo.DEFAULT_TARGET_NAME"

// endregion