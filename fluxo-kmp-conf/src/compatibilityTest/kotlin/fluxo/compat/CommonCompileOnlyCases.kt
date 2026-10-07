package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * `commonCompileOnly` keeps a dependency out of what JVM consumers resolve at runtime, while
 * JS, Wasm and Native, which can't take a compile-only dependency, publish it as `api` (KGP
 * warns otherwise). Common code referencing it compiles on every target.
 */
internal fun runKmpCommonCompileOnlyCase(row: Map<String, String>, tempDir: Path) {
    val output = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-common-compile-only",
        projectDir = tempDir.resolve(row.getValue("id") + "-common-compile-only"),
        tasks = listOf("compileKotlinJvm", "compileKotlinJs", "printVariantDependencies"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + "Unsupported `compileOnly` Dependencies",
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(commonCompileOnlyBuildScript(row))
        projectDir.resolve("src/commonMain/kotlin").createDirectories().resolve("C.kt")
            .writeText(
                "package compat\n\n" +
                    "fun dispatcher(): Any = kotlinx.coroutines.Dispatchers.Default\n",
            )
    }.output

    val variants = output.lines().filter { it.startsWith(VARIANT_MARKER) }.associate {
        it.removePrefix(VARIANT_MARKER).substringBefore('=') to it.substringAfter('=')
    }
    for (jvm in listOf("jvmApiElements", "jvmRuntimeElements")) {
        check(variants[jvm]?.contains(COROUTINES) == false) {
            "A compile-only dependency must not reach JVM consumers ($jvm): $variants"
        }
    }
    check(variants["jsApiElements"]?.contains(COROUTINES) == true) {
        "JS has no compile-only dependencies, so it must publish it as api: $variants"
    }
}

private const val COROUTINES = "kotlinx-coroutines-core"

private const val VARIANT_MARKER = "FLUXO_COMPAT_VARIANT "

private fun commonCompileOnlyBuildScript(row: Map<String, String>) =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}"
        id("${pluginId()}") version "${pluginVersion()}"
    }

    fkcSetupMultiplatform(
        config = {
            setupVerification = false
            enablePublication = false
            enableGradleDoctor = false
            setupCoroutines = false
        },
        kmp = {
            jvm()
            js()
        },
    )

    kotlin {
        commonCompileOnly("org.jetbrains.kotlinx:$COROUTINES:1.10.2", project)
    }

    val variants = listOf("jvmApiElements", "jvmRuntimeElements", "jsApiElements")
    tasks.register("printVariantDependencies") {
        val lines = variants.map { name ->
            val deps = configurations.getByName(name).allDependencies.map { it.name }
            "$VARIANT_MARKER" + name + "=" + deps
        }
        doLast { lines.forEach(::println) }
    }
    """.trimIndent()
