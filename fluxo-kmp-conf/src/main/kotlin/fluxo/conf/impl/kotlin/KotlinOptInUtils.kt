package fluxo.conf.impl.kotlin


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

internal fun KotlinConfig.prepareTestOptIns(): Set<String> {
    return prepareOptIns(
        optIns = optIns,
        setupCoroutines = setupCoroutines,
        optInInternal = optInInternal,
        isTest = true,
    )
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
