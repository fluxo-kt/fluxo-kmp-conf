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
 * Test compilations get these, and every compilation with [KotlinConfig.optInInternal]. They are
 * never module-wide: each is added only where coroutines are on the classpath
 * ([coroutinesOptIns]).
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
 * Null when coroutines are not set up.
 */
internal fun KotlinConfig.coroutinesOptIns(
    graph: Provider<ResolvedComponentResult>,
): Provider<List<String>>? {
    if (!setupCoroutines) return null
    return graph.map {
        if (it.hasCoroutinesCore()) COROUTINES_OPT_INS else emptyList()
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
