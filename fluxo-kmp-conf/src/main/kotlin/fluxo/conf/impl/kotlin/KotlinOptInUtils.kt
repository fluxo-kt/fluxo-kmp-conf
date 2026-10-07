package fluxo.conf.impl.kotlin

import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Provider

/**
 * Markers that only let the author write a construct on purpose, and change nothing until used.
 * `kotlin.RequiresOptIn` is not here: the compiler ignores an opt-in to it.
 */
internal val DEFAULT_OPT_INS = listOf(
    // `contract { }` blocks.
    "kotlin.contracts.ExperimentalContracts",
    // `@ObjCName`, naming a declaration in the Objective-C/Swift API.
    "kotlin.experimental.ExperimentalObjCName",
    // `@BuilderInference` and `@OverloadResolutionByLambdaReturnType`.
    "kotlin.experimental.ExperimentalTypeInference",
)

private val DELICATE_COROUTINES_API_OPT_INS = listOf(
    "kotlinx.coroutines.DelicateCoroutinesApi",
    "kotlinx.coroutines.ExperimentalCoroutinesApi",
    "kotlinx.coroutines.FlowPreview",
    "kotlinx.coroutines.InternalCoroutinesApi",
)

/**
 * Test-only opt-ins. Compile tasks pass `withCoroutines = false` and get the coroutines markers
 * from [coroutinesTestOptIns], which checks their classpath; a source set's `languageSettings`
 * take only a fixed list, so shared test source sets get them whenever coroutines are set up.
 */
internal fun KotlinConfig.prepareTestOptIns(withCoroutines: Boolean): Set<String> = prepareOptIns(
    optIns = optIns,
    setupCoroutines = setupCoroutines && withCoroutines,
    optInInternal = optInInternal,
    isTest = true,
)

/**
 * The coroutines markers for a test compilation, only when its dependency [graph] has
 * `kotlinx-coroutines-core`, which declares all four: an opt-in to a marker the compiler can't
 * find prints "Opt-in requirement marker … is unresolved" on every compile. The resolved graph
 * decides, not whether fluxo added the dependency, so a consumer who declares coroutines
 * themselves keeps them (`InternalCoroutinesApi` is error-level: without the opt-in their tests
 * stop compiling). Null when nothing is added here: coroutines not set up, or [optInInternal]
 * already opts every compilation in, as the consumer asked.
 */
internal fun KotlinConfig.coroutinesTestOptIns(
    graph: Provider<ResolvedComponentResult>,
): Provider<List<String>>? {
    if (!setupCoroutines || optInInternal) return null
    return graph.map {
        if (it.hasCoroutinesCore()) DELICATE_COROUTINES_API_OPT_INS else emptyList()
    }
}

/** Any platform variant counts (`kotlinx-coroutines-core-jvm`, `…-iosarm64`, …). */
private fun ResolvedComponentResult.hasCoroutinesCore(): Boolean {
    val seen = HashSet<ResolvedComponentResult>()
    val queue = ArrayDeque<ResolvedComponentResult>().apply { add(this@hasCoroutinesCore) }
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        if (!seen.add(component)) continue
        val module = component.moduleVersion
        if (module?.group == "org.jetbrains.kotlinx" &&
            module.name.startsWith("kotlinx-coroutines-core")
        ) {
            return true
        }
        for (dependency in component.dependencies) {
            if (dependency is ResolvedDependencyResult) queue.add(dependency.selected)
        }
    }
    return false
}

internal fun prepareOptIns(
    optIns: Collection<String>,
    setupCoroutines: Boolean,
    optInInternal: Boolean,
    isTest: Boolean = false,
): Set<String> {
    val set = LinkedHashSet(optIns)
    if (setupCoroutines && (isTest || optInInternal)) {
        set.addAll(DELICATE_COROUTINES_API_OPT_INS)
    }
    return set
}
