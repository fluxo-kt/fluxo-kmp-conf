@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.kotlin.KOTLIN_2_0
import fluxo.log.w
import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinTargetWithNodeJsDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl


internal val DEFAULT_COMMON_JS_CONFIGURATION: KotlinTargetContainer<KotlinTarget>.() -> Unit =
    {
        target(DEFAULT_COMMON_JS_CONF)
    }

public val DEFAULT_COMMON_JS_CONF: KotlinTarget.() -> Unit = {
    // set up browser & nodejs environment + test timeouts
    if (this is KotlinJsTargetDsl) {
        browser {
            testTimeout()
        }

        compilations.configureEach {
            // Fallback for Kotlin versions before 2.0
            try {
                @Suppress("DEPRECATION")
                kotlinOptions {
                    moduleKind = "es"
                    sourceMap = true
                    try {
                        useEsClasses = true
                    } catch (_: Error) {
                    }
                }
            } catch (_: Throwable) {
            }

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
            testTimeout()
        }
    }

    if (this is KotlinWasmJsTargetDsl) {
        if (ENABLE_D8) {
            d8 {
                testTimeout()
            }
        }
        // Apply Binaryen optimizer to the WASM target.
        if (KotlinVersion.CURRENT < KOTLIN_2_0) {
            // Binaryen is enabled by default in Kotlin 2.0.
            applyBinaryen()
        }
    } else {
        // KotlinWasmTargetDsl is incomplete before the Kotlin 2.0
        try {
            @Suppress("ControlFlowWithEmptyBody")
            if (this is KotlinWasmTargetDsl) {
                // Binaryen is enabled by default in Kotlin 2.0.
                // applyBinaryen()

                // TODO: Uncomment once compiled for Kotlin 2.0
                // binaries.executable()
            }
        } catch (_: Error) {
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
