@file:Suppress("TooManyFunctions")
@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.CommonJvm.Companion.ANDROID
import fluxo.conf.impl.compileOnlyAndLog
import fluxo.conf.impl.implementation
import fluxo.conf.impl.implementationAndLog
import fluxo.conf.impl.isTestRelated
import fluxo.conf.impl.kotlin
import fluxo.conf.impl.kotlin.KOTLIN_SOURCE_SETS_DEPENDS_ON_DEPRECATION
import fluxo.conf.impl.maybeRegister
import fluxo.conf.kmp.SourceSetBundle
import fluxo.log.w
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetsContainer
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family


// region Named source sets and bundles

/**
 * No need to make public, Kotlin plugin already covers it.
 *
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformSourceSetConventions.commonMain
 */
internal val NamedDomainObjectCollection<out KotlinSourceSet>.commonMain: KotlinSourceSet
    get() = getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)

/**
 * No need to make public, Kotlin plugin already covers it.
 *
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformSourceSetConventions.commonTest
 */
internal val NamedDomainObjectCollection<out KotlinSourceSet>.commonTest: KotlinSourceSet
    get() = getByName(KotlinSourceSet.COMMON_TEST_SOURCE_SET_NAME)

public val KotlinSourceSetContainer.common: SourceSetBundle
    get() = sourceSets.let { SourceSetBundle(it.commonMain, it.commonTest) }

public val NamedDomainObjectCollection<out KotlinSourceSet>.common: SourceSetBundle
    get() = SourceSetBundle(commonMain, commonTest)


// TODO: Create bundles once and reuse or at least make them lazy evaluatable.

/** Parent [SourceSetBundle] for all JVM-based targets (JVM, Android) */
public val KotlinSourceSetContainer.commonJvm: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.CommonJvm.COMMON_JVM)

/** Parent [SourceSetBundle] for all non-JVM targets (JS, Native) */
public val KotlinSourceSetContainer.commonNonJvm: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.NON_JVM)

/** Parent [SourceSetBundle] for all Kotlin/JS-based targets (JS, WASM) */
public val KotlinSourceSetContainer.commonJs: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.CommonJs.COMMON_JS)

/** Parent [SourceSetBundle] for all Kotlin/WASM-based targets (WASM-JS, WASM-WASI) */
public val KotlinSourceSetContainer.commonWasm: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.CommonJs.CommonWasm.COMMON_WASM)

/** Parent [SourceSetBundle] for all Kotlin/Native targets */
public val KotlinSourceSetContainer.commonNative: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.Native.NATIVE)

/** Parent [SourceSetBundle] for all unix-like targets (Darwin/Apple, Linux, AndroidNative) */
public val KotlinSourceSetContainer.commonNix: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.Native.Nix.NIX)

/** Parent [SourceSetBundle] for all Darwin/Apple-based targets (iOS, macOS, tvOS, watchOS) */
public val KotlinSourceSetContainer.commonApple: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.Native.Nix.Apple.APPLE)

/** Parent [SourceSetBundle] for all iOS targets */
public val KotlinSourceSetContainer.commonIos: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.Native.Nix.Apple.Ios.IOS)

/** Parent [SourceSetBundle] for all macOS targets */
public val KotlinSourceSetContainer.commonMacos: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.Native.Nix.Apple.Macos.MACOS)

/** Parent [SourceSetBundle] for all tvOS targets */
public val KotlinSourceSetContainer.commonTvos: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.Native.Nix.Apple.Tvos.TVOS)

/** Parent [SourceSetBundle] for all watchOS targets */
public val KotlinSourceSetContainer.commonWatchos: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.Native.Nix.Apple.Watchos.WATCHOS)

/** Parent [SourceSetBundle] for all Linux targets */
public val KotlinSourceSetContainer.commonLinux: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.Native.Nix.Linux.LINUX)

/** Parent [SourceSetBundle] for all MinGW/Win targets */
public val KotlinSourceSetContainer.commonMingw: SourceSetBundle
    get() = sourceSets.bundle(KmpTargetContainerImpl.NonJvm.Native.Mingw.MINGW)

// endregion


// region Sets of source sets

/** [SourceSetBundle]s for all enabled targets */
public val <E> E.allSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = allTargetsSet

/** [SourceSetBundle]s for all enabled targets */
public val <E> E.allTargetsSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = targets.toSourceSetBundles()

/** androidJvm, jvm */
public val <E> E.javaSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = targets.matching {
        it.platformType == KotlinPlatformType.androidJvm ||
          it.platformType == KotlinPlatformType.jvm
    }.toSourceSetBundles()

/** androidJvm */
public val <E> E.androidSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = targets.matching { it.platformType == KotlinPlatformType.androidJvm }
      .toSourceSetBundles()

/** js */
public val <E> E.jsSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = targets.matching { it.platformType == KotlinPlatformType.js }.toSourceSetBundles()


/** All Kotlin/Native targets */
public val <E> E.nativeSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = nativeSourceSets()


public val <E> E.linuxSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = nativeSourceSets(Family.LINUX)

public val <E> E.mingwSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = nativeSourceSets(Family.MINGW)

public val <E> E.androidNativeSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = nativeSourceSets(Family.ANDROID)


/** All Apple (Darwin) targets */
public val <E> E.appleSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = nativeSourceSets(Family.IOS, Family.OSX, Family.WATCHOS, Family.TVOS)

/** All Apple (Darwin) targets */
public val <E> E.darwinSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = appleSet

public val <E> E.iosSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = nativeSourceSets(Family.IOS)

public val <E> E.watchosSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = nativeSourceSets(Family.WATCHOS)

public val <E> E.tvosSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = nativeSourceSets(Family.TVOS)

public val <E> E.macosSet: Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer
    get() = nativeSourceSets(Family.OSX)


private fun <E> E.nativeSourceSets(vararg families: Family = Family.values()): Set<SourceSetBundle>
  where E : KotlinSourceSetContainer, E : KotlinTargetsContainer =
  targets.filter { it is KotlinNativeTarget && it.konanTarget.family in families }
    .toSourceSetBundles()

context(KotlinSourceSetContainer)
private fun Iterable<KotlinTarget>.toSourceSetBundles(): Set<SourceSetBundle> {
    return mapNotNullTo(LinkedHashSet()) {
        when (it.platformType) {
            KotlinPlatformType.common -> null
            else -> bundleFor(it)
        }
    }
}

// endregion


// region SourceSetBundle utils

internal fun KotlinSourceSetContainer.bundleFor(target: KotlinTarget) = sourceSets.bundleFor(target)

internal fun NamedDomainObjectContainer<out KotlinSourceSet>.bundleFor(
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

public fun KotlinSourceSetContainer.bundle(
    name: String,
    androidLayoutV2: Boolean? = null,
): SourceSetBundle = sourceSets.bundle(name, androidLayoutV2)

public fun NamedDomainObjectContainer<out KotlinSourceSet>.bundle(
    name: String,
    androidLayoutV2: Boolean? = null,
): SourceSetBundle {
    val mainSourceSet = maybeCreate(name + MAIN_SOURCE_SET_POSTFIX)

    // region Support for androidSourceSetLayout v2
    // https://kotlinlang.org/docs/whatsnew18.html#kotlinsourceset-naming-schema
    /** @see fluxo.conf.dsl.container.impl.target.TargetAndroidContainer.setup */
    val isAndroid = name == ANDROID
    if (isAndroid) {
        val useV1 = androidLayoutV2?.not()
          ?: names.let { "androidAndroidTest" in it || "androidTest" in it }
        val instrumentedTest =
          maybeCreate(if (!useV1) "androidInstrumentedTest" else "androidAndroidTest")
        return SourceSetBundle(
          main = mainSourceSet,
          test = maybeCreate(if (!useV1) "androidUnitTest" else "androidTest"),
          moreTests = arrayOf(instrumentedTest),
        )
    }
    // endregion

    return SourceSetBundle(
      main = mainSourceSet,
      test = maybeCreate(name + TEST_SOURCE_SET_POSTFIX),
    )
}

/**
 *
 * @see kotlin.properties.PropertyDelegateProvider
 * @see kotlin.properties.ReadOnlyProperty
 */
public fun NamedDomainObjectContainer<out KotlinSourceSet>.bundle(
    name: String? = null,
): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, SourceSetBundle>> =
  PropertyDelegateProvider { _, property ->
      val bundle = bundle(name = name ?: property.name)
      ReadOnlyProperty { _, _ -> bundle }
  }

public fun KotlinSourceSetContainer.bundle(
    name: String? = null,
): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, SourceSetBundle>> =
  sourceSets.bundle(name)

internal const val MAIN_SOURCE_SET_NAME = "main"
internal const val TEST_SOURCE_SET_NAME = "test"
internal const val MAIN_SOURCE_SET_POSTFIX = "Main"
internal const val TEST_SOURCE_SET_POSTFIX = "Test"

// endregion


// region Dependecies declaration

public operator fun SourceSetBundle.plus(other: SourceSetBundle): Set<SourceSetBundle> =
  this + setOf(other)

public operator fun SourceSetBundle.plus(other: Set<SourceSetBundle>): Set<SourceSetBundle> =
  setOf(this) + other

@Deprecated(KOTLIN_SOURCE_SETS_DEPENDS_ON_DEPRECATION)
public infix fun SourceSetBundle.dependsOn(other: SourceSetBundle) {
    main.dependsOn(other.main)
    test.dependsOn(other.test)
    moreTests?.forEach {
        it.dependsOn(other.test)
    }
}

@Deprecated(KOTLIN_SOURCE_SETS_DEPENDS_ON_DEPRECATION)
public infix fun Iterable<SourceSetBundle>.dependsOn(other: Iterable<SourceSetBundle>) {
    forEach { left ->
        other.forEach { right ->
            @Suppress("DEPRECATION")
            left.dependsOn(right)
        }
    }
}

@Deprecated(KOTLIN_SOURCE_SETS_DEPENDS_ON_DEPRECATION)
@Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
public infix fun SourceSetBundle.dependsOn(other: Iterable<SourceSetBundle>) {
    listOf(this) dependsOn other
}

@Deprecated(KOTLIN_SOURCE_SETS_DEPENDS_ON_DEPRECATION)
@Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
public infix fun Iterable<SourceSetBundle>.dependsOn(other: SourceSetBundle) {
    this dependsOn listOf(other)
}


@Deprecated(KOTLIN_SOURCE_SETS_DEPENDS_ON_DEPRECATION)
@Suppress("DeprecatedCallableAddReplaceWith")
public infix fun KotlinSourceSet.dependsOn(other: SourceSetBundle) {
    dependsOn(if (isTestRelated()) other.test else other.main)
}

@JvmName("dependsOnBundles")
@Deprecated(KOTLIN_SOURCE_SETS_DEPENDS_ON_DEPRECATION)
public infix fun KotlinSourceSet.dependsOn(other: Iterable<SourceSetBundle>) {
    other.forEach { right ->
        @Suppress("DEPRECATION")
        dependsOn(right)
    }
}

@Deprecated(KOTLIN_SOURCE_SETS_DEPENDS_ON_DEPRECATION)
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


public fun <E> E.commonCompileOnly(
    dependencyNotation: Any,
    project: Project? = null,
    addConstraint: Boolean = true,
) where E : KotlinSourceSetContainer, E : KotlinTargetsContainer {
    val p = try {
        project
            ?: targets.firstOrNull()?.project
            ?: throw NullPointerException("Please, provide project")
    } catch (e: Throwable) {
        throw GradleException(
          "Unable to add common compileOnly dependency '$dependencyNotation': $e",
          e,
        )
    }

    with(p) {
        val sourceSets = sourceSets
        sourceSets.commonMain.dependencies {
            compileOnlyAndLog(dependencyNotation)
        }

        /*
         * A compileOnly dependencies aren't applicable for Kotlin/Native.
         * Use `implementation` or `api` dependency type instead.
         * Set it in the shared "native" target.
         */
        /** @see commonNative */
        val sourceSetName = KmpTargetContainerImpl.NonJvm.Native.NATIVE + MAIN_SOURCE_SET_POSTFIX
        sourceSets.maybeRegister(sourceSetName) {
            dependencies {
                implementationAndLog(dependencyNotation)
            }
        }

        if (addConstraint) {
            if (dependencyNotation is Dependency && dependencyNotation.version.isNullOrEmpty()) {
                p.logger.w(
                  "Dependency version is empty, " +
                    "can't add a constraint: $dependencyNotation",
                )
            } else {
                dependencies.constraints.implementation(dependencyNotation)
            }
        }
    }
}

// endregion
