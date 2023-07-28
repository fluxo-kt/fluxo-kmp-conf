package fluxo.conf.impl.kotlin


internal val DEFAULT_OPT_INS = listOf(
    "kotlin.RequiresOptIn",
    "kotlin.contracts.ExperimentalContracts",
    "kotlin.experimental.ExperimentalObjCName",
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
