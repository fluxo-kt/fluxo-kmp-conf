package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.log.logDecision
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

/**
 * Kotlin/JS and Kotlin/Wasm compile only against the standard library of the compiler's exact
 * version. Measured on Kotlin 2.4.20: stdlib 2.4.0 fails with "The Kotlin/JS standard library has
 * an older version … Such a configuration is not supported", stdlib 2.3.21 with "… has the ABI
 * version (2.3.0) that is not compatible with the compiler's current ABI compatibility level";
 * JVM and shared metadata compile against both. `kotlin-test` 2.3.21 fails the same way, while
 * `kotlin-dom-api-compat` 2.3.21 is accepted. So a `kotlinCoreLibraries` floor below the compiler
 * stays on JVM and Android, while this JS or Wasm compilation resolves the compiler's stdlib and
 * kotlin-test. Kotlin/Native always uses the stdlib bundled with its compiler.
 */
internal fun KotlinCompilation<*>.useCompilerStdlib(conf: FluxoConfigurationExtensionImpl) {
    val floor = conf.kotlinConfig.coreLibs
    val compiler = KOTLIN_PLUGIN_VERSION_STRING
    if (floor == compiler) return
    check(!conf.singleKotlinStdlibVersion) {
        "Kotlin/JS and Kotlin/Wasm need the stdlib of the compiler's exact version ($compiler), " +
            "but singleKotlinStdlibVersion = true gives them kotlinCoreLibraries ($floor) in " +
            "${project.path}. Remove singleKotlinStdlibVersion, or set kotlinCoreLibraries to " +
            "$compiler."
    }
    val names = setOfNotNull(compileDependencyConfigurationName, runtimeDependencyConfigurationName)
    project.configurations.configureEach {
        if (name !in names) return@configureEach
        resolutionStrategy.eachDependency {
            val module = requested.name
            val isCompilerBound = module.startsWith("kotlin-stdlib") ||
                module.startsWith("kotlin-test")
            if (requested.group == "org.jetbrains.kotlin" && isCompilerBound) {
                useVersion(compiler)
                because("Kotlin/JS and Wasm accept only the compiler's stdlib and kotlin-test")
            }
        }
    }
    if (name == KotlinCompilation.MAIN_COMPILATION_NAME) {
        conf.ctx.logDecision(
            project,
            setting = "Kotlin stdlib (${target.name})",
            value = compiler,
            reason = "JS and Wasm compile only against the compiler's stdlib; JVM and Android " +
                "keep kotlinCoreLibraries $floor, Native its bundled one",
            howToChange = "set kotlinCoreLibraries to $compiler, or singleKotlinStdlibVersion",
        )
    }
}
