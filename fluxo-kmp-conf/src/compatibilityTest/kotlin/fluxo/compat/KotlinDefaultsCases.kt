package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Every compiler default fluxo adds can be switched off for the whole build, and
 * `DISABLE_KOTLIN_DEFAULTS` beats the module's DSL, so CI can rely on it: here the module sets
 * `validateBytecode = true` and the flag must still be gone. A misspelled name fails the build
 * instead of being ignored, as Gradle ignores an unknown property. The first run is the control:
 * without the switch both flags are passed.
 */
internal fun runKotlinDefaultsSwitchCase(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve(row.getValue("id") + "-defaults-off")
    fun run(arguments: List<String>, expectFailure: List<String> = emptyList()) = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-defaults-off",
        projectDir = projectDir,
        tasks = listOf("compileKotlin"),
        arguments = arguments,
        expectFailure = expectFailure,
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) {
        val setting = "setupCoroutines = false"
        val script = markerKotlinJvmBuildScript(row)
            .replace(setting, "$setting\n    validateBytecode = true")
        it.resolve("build.gradle.kts").writeText(script + ARGS_PROBE)
        writeKotlinJvmSources(it)
    }.output.substringAfter(ARGS_MARKER, "").substringBefore('\n')

    val on = run(emptyList())
    check("-Xjsr305=strict" in on && "-Xvalidate-bytecode" in on) { "Control lacks flags: $on" }
    val off = run(listOf("-PDISABLE_KOTLIN_DEFAULTS=-Xjsr305,validate-bytecode"))
    check(off.isNotEmpty() && "-Xjsr305" !in off && "-Xvalidate-bytecode" !in off) {
        "DISABLE_KOTLIN_DEFAULTS left flags on: '$off'"
    }
    run(listOf("-PDISABLE_KOTLIN_DEFAULTS=jsr3O5"), expectFailure = listOf("Did you mean jsr305?"))
}

private const val ARGS_MARKER = "FLUXO_COMPAT_ARGS="

private val ARGS_PROBE =
    """

    tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
        val args = compilerOptions.freeCompilerArgs
        doFirst { println("$ARGS_MARKER" + args.get()) }
    }
    """.trimIndent()
