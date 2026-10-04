package fluxo.conf.impl

// Kotlin source set naming. Kept out of the `Fkc` multifile class (the top-level FkcSetup*.kt and
// friends): one of its parts needs @Suppress("INVISIBLE_*"), and then any edit to any part
// recompiles the facade together with callers of its `internal const val` getters, and the K2 IR
// optimizer crashes on those calls (AGENTS.md, the Kotlin 2.2 IR optimizer surprise).

internal const val MAIN_SOURCE_SET_NAME = "main"
internal const val TEST_SOURCE_SET_NAME = "test"
internal const val MAIN_SOURCE_SET_POSTFIX = "Main"
internal const val TEST_SOURCE_SET_POSTFIX = "Test"
