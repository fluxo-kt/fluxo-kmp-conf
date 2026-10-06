package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * A `kotlinCoreLibraries` floor below the compiler: Kotlin/JS and Kotlin/Wasm compile only
 * against the compiler's exact stdlib and kotlin-test (older: "not supported" / "ABI version …
 * not compatible"), so they must get those while JVM keeps the floor.
 * `singleKotlinStdlibVersion` restores one version for every platform, which with JS and Wasm
 * targets fails at configuration naming the setting.
 */
internal fun runKmpStdlibSplitCase(row: Map<String, String>, tempDir: Path) {
    val compiler = row.getValue("kgpVersion")
    fun case(single: Boolean, expectFailure: List<String>) = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-stdlib-split",
        projectDir = tempDir.resolve(row.getValue("id") + "-stdlib-split"),
        tasks = listOf(
            "compileKotlinJvm",
            "compileKotlinJs",
            "compileKotlinWasmJs",
            "compileTestKotlinJs",
            "compileTestKotlinWasmJs",
        ),
        expectFailure = expectFailure,
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(stdlibSplitBuildScript(row, single))
        projectDir.resolve("src/commonMain/kotlin").createDirectories().resolve("S.kt")
            .writeText("package compat\n\nfun joined(): String = listOf(1, 2).joinToString()\n")
        // kotlin("test") resolves at kotlinCoreLibraries and is bound to the compiler on JS/Wasm.
        projectDir.resolve("src/commonTest/kotlin").createDirectories().resolve("T.kt").writeText(
            "package compat\n\nclass T {\n    @kotlin.test.Test\n" +
                "    fun joins() = kotlin.test.assertEquals(\"1, 2\", joined())\n}\n",
        )
    }.output

    val output = case(single = false, expectFailure = emptyList())
    val stdlib = output.lines().filter { it.startsWith(STDLIB_MARKER) }.associate {
        it.removePrefix(STDLIB_MARKER).substringBefore('=') to it.substringAfter('=')
    }
    check(stdlib["compileKotlinJvm"]?.contains("kotlin-stdlib-$FLOOR.jar") == true) {
        "JVM must keep the floor stdlib $FLOOR: $stdlib"
    }
    for (task in listOf("compileKotlinJs", "compileKotlinWasmJs")) {
        check(stdlib[task]?.contains(compiler) == true && stdlib[task]?.contains(FLOOR) == false) {
            "$task must get the compiler's stdlib $compiler: $stdlib"
        }
    }
    case(single = true, expectFailure = listOf("singleKotlinStdlibVersion = true"))
}

/** One minor below the newest tested Kotlin. */
private const val FLOOR = "2.3.21"

private const val STDLIB_MARKER = "FLUXO_COMPAT_STDLIB "

private fun stdlibSplitBuildScript(row: Map<String, String>, single: Boolean) =
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
            kotlinCoreLibraries = "$FLOOR"
            ${if (single) "singleKotlinStdlibVersion = true" else ""}
        },
        kmp = {
            jvm()
            js()
            wasmJs()
        },
    )

    kotlin {
        sourceSets.commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool<*>>()
        .configureEach {
            val libs = libraries
            val task = name
            doFirst {
                val names = libs.files.map { it.name }.filter { it.startsWith("kotlin-stdlib") }
                println("$STDLIB_MARKER" + task + "=" + names)
            }
        }
    """.trimIndent()
