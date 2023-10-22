import fluxo.conf.dsl.container.KotlinTargetContainer
import org.gradle.api.attributes.Attribute
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinTargetWithNodeJsDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl


private val WASM_ATTRIBUTE = Attribute.of("wasmType", String::class.java)

internal val DEFAULT_COMMON_JS_CONFIGURATION: KotlinTargetContainer<KotlinTarget>.() -> Unit =
    {
        target {
            if (this is KotlinJsTargetDsl) {
                defaults()
            } else if (this is KotlinTargetWithNodeJsDsl) {
                nodejs {
                    testTimeout(seconds = TEST_TIMEOUT)
                }
            }

            // Workaround the attribute error when used both wasmJs and wasmWasi targets.
            if (this is KotlinWasmTargetDsl) {
                attributes.attribute(WASM_ATTRIBUTE, targetName)
            }
        }
    }

public fun KotlinJsTargetDsl.defaults() {
    // set up browser & nodejs environment + test timeouts
    testTimeout()

    // Apply Binaryen optimizer to the WASM target
    if (this is KotlinWasmJsTargetDsl) {
        applyBinaryen()
    }

    compilations.all {
        kotlinOptions {
            moduleKind = "es"
            useEsClasses = true
            sourceMap = true
            metaInfo = true
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

public fun KotlinJsTargetDsl.testTimeout(seconds: Int = TEST_TIMEOUT) {
    browser {
        testTimeout(seconds)
    }
    nodejs {
        testTimeout(seconds)
    }
    if (this is KotlinWasmJsTargetDsl) {
        d8 {
            testTimeout(seconds)
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
