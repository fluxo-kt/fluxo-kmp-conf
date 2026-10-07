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

/**
 * Who gets these: [getsCoroutinesOptIns]. They are never module-wide: each compilation gets them
 * only where coroutines are on its classpath ([coroutinesOptIns]).
 */
internal val COROUTINES_OPT_INS = listOf(
    "kotlinx.coroutines.DelicateCoroutinesApi",
    "kotlinx.coroutines.ExperimentalCoroutinesApi",
    "kotlinx.coroutines.FlowPreview",
    "kotlinx.coroutines.InternalCoroutinesApi",
)

/**
 * [COROUTINES_OPT_INS] for one compilation, only when its dependency [graph] has
 * `kotlinx-coroutines-core`, which declares all four: an opt-in to a marker the compiler can't
 * find prints "Opt-in requirement marker … is unresolved" on every compile, an error wherever
 * warnings are errors (main compilations on CI and release builds). The resolved graph decides,
 * not whether fluxo added the dependency, so a consumer who declares coroutines themselves keeps
 * them (`InternalCoroutinesApi` is error-level: without the opt-in their code stops compiling).
 * Null when the code gets none ([getsCoroutinesOptIns]); [graph] is lazy, so nothing resolves then.
 */
internal fun KotlinConfig.coroutinesOptIns(
    isTest: Boolean,
    graph: Provider<ResolvedComponentResult>,
): Provider<List<String>>? {
    if (!getsCoroutinesOptIns(isTest)) return null
    return graph.map {
        if (it.hasCoroutinesCore()) COROUTINES_OPT_INS else emptyList()
    }
}

/**
 * Which code gets [COROUTINES_OPT_INS]: test code, and main code with [KotlinConfig.optInInternal].
 * The one place this rule lives: compile tasks and the IDE-only shared source sets both read it,
 * so they can't disagree about which code may use those APIs.
 */
internal fun KotlinConfig.getsCoroutinesOptIns(isTest: Boolean): Boolean =
    setupCoroutines && (isTest || optInInternal)

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
