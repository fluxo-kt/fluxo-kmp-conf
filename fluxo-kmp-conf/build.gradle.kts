plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.build.config)
}

group = "io.github.fluxo-kt"
version = libs.versions.version.get()
description =
    "Convenience Gradle plugin for reliable configuration of Kotlin & KMP projects. Made by Fluxo."
val pluginId = libs.plugins.fluxo.conf.get().pluginId

setupGradlePlugin(
    pluginId = pluginId,
    pluginName = "fluxo-kmp-conf",
    pluginClass = "fluxo.conf.FluxoKmpConfPlugin",
    displayName = "Fluxo KMP Configuration",
    tags = listOf(
        "kotlin",
        "kotlin-multiplatform",
        "android",
        "gradle-configuration",
        "convenience",
    ),
) {
    githubProject = "fluxo-kt/fluxo-kmp-conf"
    enableSpotless = true
    setupCoroutines = false
    experimentalLatestCompilation = true

    publicationConfig {
        developerId = "amal"
        developerName = "Artyom Shendrik"
        developerEmail = "artyom.shendrik@gmail.com"
    }

    apiValidation {
        nonPublicMarkers.add("fluxo.annotation.InternalFluxoApi")
        nonPublicMarkers.add("kotlin.jvm.JvmSynthetic")
        // sealed classes constructors are not actually public
        ignoredClasses.add("kotlin.jvm.internal.DefaultConstructorMarker")
    }
}

// Exclude Kotlin stdlib from the implementation classpath entirely
configurations.implementation {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

dependencies {
    // Spotless util classes are used internally
    implementation(libs.plugin.spotless)
    // Detekt ReportMergeTask is used internally
    implementation(libs.plugin.detekt)

    compileOnly(libs.detekt.core)
    compileOnly(libs.ktlint)

    compileOnly(libs.plugin.android)
    compileOnly(libs.plugin.intellij)
    compileOnly(libs.plugin.jetbrains.compose)
    compileOnly(libs.plugin.kotlin)
    compileOnly(libs.plugin.ksp)

    compileOnly(libs.plugins.gradle.enterprise.toModuleDependency())
}

buildConfig {
    className("BuildConstants")
    packageName("fluxo.conf.data")
    buildConfigField("String", "PLUGIN_ID", "\"$pluginId\"")
    buildConfigField("int", "DEFAULT_ANDROID_MIN_SDK", libs.versions.androidMinSdk.get())
    buildConfigField("int", "DEFAULT_ANDROID_TARGET_SDK", libs.versions.androidTargetSdk.get())
    buildConfigField("int", "DEFAULT_ANDROID_COMPILE_SDK", libs.versions.androidCompileSdk.get())

    fun buildConfigField(
        name: String,
        p: Provider<PluginDependency>,
        alias: String? = null,
        alias2: String? = null,
        implementation: Boolean = false,
    ) {
        val aliasName = alias ?: name.lowercase().replace('_', '-')
        buildConfigField("String", "${name}_PLUGIN_ALIAS", "\"$aliasName\"")

        alias2?.let {
            buildConfigField("String", "${name}_PLUGIN_ALIAS2", "\"$it\"")
        }

        val pd = p.get()
        val pluginId = pd.pluginId
        buildConfigField("String", "${name}_PLUGIN_ID", "\"$pluginId\"")

        "${pd.version}".ifBlank { null }?.let { version ->
            buildConfigField("String", "${name}_PLUGIN_VERSION", "\"$version\"")
        }

        p.toModuleDependency().let { dependency ->
            when {
                implementation -> dependencies.implementation(dependency)
                else -> dependencies.compileOnly(dependency)
            }
        }
    }

    buildConfigField("KOTLIN_SAM_RECEIVER", libs.plugins.kotlin.sam.receiver)
    buildConfigField(
        "KOTLINX_BCV",
        libs.plugins.kotlinx.binCompatValidator,
        alias2 = "kotlinx-binCompatValidator",
    )
    buildConfigField("FLUXO_BCV_JS", libs.plugins.fluxo.bcv.js)
    buildConfigField("DOKKA", libs.plugins.dokka)
    buildConfigField("GRADLE_PLUGIN_PUBLISH", libs.plugins.gradle.plugin.publish)
    buildConfigField("COMPLETE_KOTLIN", libs.plugins.complete.kotlin)
    buildConfigField("DEPS_VERSIONS", libs.plugins.deps.versions, implementation = true)
    buildConfigField("DEPS_ANALYSIS", libs.plugins.deps.analysis)
    buildConfigField("DEPS_GUARD", libs.plugins.deps.guard, implementation = true)
    buildConfigField("TASK_TREE", libs.plugins.task.tree)
    buildConfigField("TASK_INFO", libs.plugins.task.info)
    buildConfigField("MODULE_DEPENDENCY_GRAPH", libs.plugins.module.dependency.graph)
    buildConfigField("BUILD_CONFIG", libs.plugins.build.config)

    // FIXME: Add support for plugin:
    buildConfigField("ABOUT_LIBRARIES", libs.plugins.about.libraries)
}
