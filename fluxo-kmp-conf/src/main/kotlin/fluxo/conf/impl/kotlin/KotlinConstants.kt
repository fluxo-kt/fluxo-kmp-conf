package fluxo.conf.impl.kotlin

internal const val KOTLIN_JVM_PLUGIN_ID = "org.jetbrains.kotlin.jvm"
internal const val KMP_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
internal const val KAPT_PLUGIN_ID = "org.jetbrains.kotlin.kapt"
internal const val KSP_PLUGIN_ID = "com.google.devtools.ksp"
internal const val JETBRAINS_COMPOSE_PLUGIN_ID = "org.jetbrains.compose"
internal const val INTELLIJ_PLUGIN_ID = "org.jetbrains.intellij"

internal const val COROUTINES_DEPENDENCY = "org.jetbrains.kotlinx:kotlinx-coroutines-core"

internal const val JUNIT_DEPENDENCY = "junit:junit:4.13.2"


internal const val KOTLIN_SOURCE_SETS_DEPENDS_ON_DEPRECATION =
    "As of Kotlin 1.9.20, " +
        "none of the source sets can depend on the compilation default source sets." +
        " Please remove this dependency edge."
