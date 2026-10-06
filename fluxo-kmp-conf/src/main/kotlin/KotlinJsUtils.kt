@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.kotlin.karmaFindsChrome
import fluxo.conf.impl.kotlin.karmaNeedsOnlyChrome
import fluxo.log.w
import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.js.KotlinWasmTargetType
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalMainFunctionArgumentsDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinTargetWithNodeJsDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest


internal val DEFAULT_COMMON_JS_CONFIGURATION: KotlinTargetContainer<KotlinTarget>.() -> Unit =
    {
        target(DEFAULT_COMMON_JS_CONF)
    }

public val DEFAULT_COMMON_JS_CONF: KotlinTarget.() -> Unit = {
    // KGP's one target class implements the JS, Wasm-JS and WASI DSLs alike, so type checks
    // can't tell these targets apart; the platform type and `wasmTargetType` can.
    val isJs = platformType == KotlinPlatformType.js
    val wasmType = if (isJs) null else (this as? KotlinWasmTargetDsl)?.wasmTargetType

    // set up browser & nodejs environment + test timeouts
    if (this is KotlinJsTargetDsl) {
        try {
            // Browser tests use KGP's default runner, Karma with headless Chrome. No
            // `testTimeout()` here: its `useMocha` would replace Karma and run them in Node.
            if (wasmType != KotlinWasmTargetType.WASI) {
                browser {
                    testTask { skipWithoutChrome() }
                }
            }
        } catch (e: Throwable) {
            try {
                project.logger.w("Failed to set up browser for target '$name': $e", e)
            } catch (_: Throwable) {
            }
        }

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        try {
            compilerOptions(JsConfAction)
        } catch (e: Throwable) {
            try {
                project.logger.w("Failed to set up compilerOptions for target '$name': $e", e)
            } catch (_: Throwable) {
            }
        }

        compilations.configureEach {
            compileTaskProvider.configure {
                try {
                    compilerOptions(JsConfAction)
                } catch (e: Throwable) {
                    logger.w("Failed to set up JS compilerOptions: $e", e)
                }
            }
        }

        try {
            useEsModules()
        } catch (_: Error) {
        }

        // Generate TypeScript declaration files
        // https://kotlinlang.org/docs/js-ir-compiler.html#preview-generation-of-typescript-declaration-files-d-ts
        binaries.executable()
        generateTypeScriptDefinitions()
    }

    if (this is KotlinTargetWithNodeJsDsl) {
        nodejs {
            // Mocha runs only JS tests; Wasm tests in Node use KGP's own runner.
            if (isJs) {
                testTimeout()
            }

            // https://kotlinlang.org/docs/whatsnew20.html#passing-arguments-to-the-main-function
            @OptIn(ExperimentalMainFunctionArgumentsDsl::class)
            try {
                passProcessArgvToMainFunction()
            } catch (_: Throwable) {
            }
        }
    }

    if (wasmType == KotlinWasmTargetType.JS && this is KotlinWasmJsTargetDsl) {
        if (ENABLE_D8) {
            try {
                d8 {
                    testTimeout()
                }
            } catch (e: Throwable) {
                try {
                    project.logger.w("Failed to set up d8 for target '$name': $e", e)
                } catch (_: Throwable) {
                }
            }
        }
        // Binaryen is enabled by default in Kotlin 2.0+; explicit applyBinaryen()
        // was removed in 2.3 and pre-2.0 WASM is no longer a supported target.
    }
}

/**
 * Skips this browser test task where Karma would launch only Chrome and finds none, so `check`
 * passes on machines without Chrome while browser tests run wherever it is installed (developer
 * machines, most CI images). Decided when the task runs: the consumer's own browser choice is
 * final by then, and the environment read never enters the configuration cache.
 */
private fun KotlinJsTest.skipWithoutChrome() {
    val env = project.providers.environmentVariablesPrefixedBy("")
    onlyIf("Karma finds the Chrome its browser tests need") { task ->
        val settings = (task as KotlinJsTest).testFrameworkSettings
        if (!karmaNeedsOnlyChrome(settings)) return@onlyIf true
        karmaFindsChrome(env.get()::get, System.getProperty("os.name")).also { found ->
            if (!found) {
                task.logger.w(
                    "${task.path} skipped: no Chrome found for these browser tests " +
                        "(Node tests still run). Install Chrome, or set CHROME_BIN to a Chrome " +
                        "or Chromium executable.",
                )
            }
        }
    }
}

private object JsConfAction : Action<KotlinJsCompilerOptions> {
    override fun execute(o: KotlinJsCompilerOptions) {
        o.moduleKind.set(JsModuleKind.MODULE_ES)
        o.sourceMap.set(true)
        try {
            o.useEsClasses.set(true)
        } catch (_: Error) {
        }

        // Automatically turns on ES classes and modules and the newly supported ES generators.
        // https://kotlinlang.org/docs/whatsnew20.html#new-compilation-target
        o.target.set("es2015")
    }
}

public fun KotlinJsSubTargetDsl.testTimeout(seconds: Int = TEST_TIMEOUT) {
    require(seconds > 0) { "Timeout seconds must be greater than 0." }
    testTask {
        useMocha { timeout = "${seconds}s" }
    }
}

/**
 * Default timeout for Kotlin/JS tests is `2s`.
 *
 * @see org.jetbrains.kotlin.gradle.targets.js.testing.mocha.KotlinMocha.DEFAULT_TIMEOUT
 */
// https://mochajs.org/#-timeout-ms-t-ms
private const val TEST_TIMEOUT = 10

/**
 * Enable D8 for Kotlin/WASM target.
 * Disabled due to errors in the Kotlin after 1.9.20-RC.
 * @TODO: Check how it can be enabled?
 */
private const val ENABLE_D8 = false
