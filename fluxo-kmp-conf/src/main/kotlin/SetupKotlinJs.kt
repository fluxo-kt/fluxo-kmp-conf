import fluxo.conf.dsl.container.KotlinTargetContainer
import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl


internal val DEFAULT_COMMON_JS_CONFIGURATION: KotlinTargetContainer<KotlinJsTargetDsl>.() -> Unit =
    {
        target {
            defaults()
        }
    }

public fun KotlinJsTargetDsl.defaults() {
    testTimeout()

    if (this is KotlinWasmTargetDsl) {
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
    if (this is KotlinWasmTargetDsl) {
        d8 {
            testTimeout(seconds)
        }
    }
}

public fun KotlinJsSubTargetDsl.testTimeout(seconds: Int = TEST_TIMEOUT) {
    require(seconds > 0) { "Timeout seconds must be greater than 0." }
    testTask(
        Action {
            useMocha { timeout = "${seconds}s" }
        },
    )
}

/**
 * Default timeout for Kotlin/JS tests is `2s`.
 *
 * @see org.jetbrains.kotlin.gradle.targets.js.testing.mocha.KotlinMocha.DEFAULT_TIMEOUT
 */
// https://mochajs.org/#-timeout-ms-t-ms
private const val TEST_TIMEOUT = 10
