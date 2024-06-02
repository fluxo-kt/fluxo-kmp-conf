plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.build.config)
}

group = "io.github.fluxo-kt"
version = libs.versions.version.get()
description =
    "Convenience Gradle plugin for reliable configuration of Kotlin & KMP projects. Made by Fluxo."
val pluginId = libs.plugins.fluxo.conf.get().pluginId

val resDir: Provider<Directory> = layout.buildDirectory
    .dir("generated/sources/fluxo/resources").map { dir ->
        val dirFile = dir.asFile
        dirFile.mkdirs()
        val targetFile = File(dirFile, "fluxo.versions.toml")
        project.file("../gradle/libs.versions.toml").useLines { lines ->
            targetFile.bufferedWriter().use { writer ->
                lines.map { it.substringBefore('#').trim() }
                    .filter { it.isNotEmpty() }
                    .joinTo(writer, separator = "\n")
            }
        }
        logger.lifecycle("   Generated: ${targetFile.relativeTo(layout.projectDirectory.asFile)}")
        dir
    }

fkcSetupGradlePlugin(
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
    kotlin = {
        sourceSets.main {
            resources.srcDir(resDir.get())
        }
    },
) {
    githubProject = "fluxo-kt/fluxo-kmp-conf"
    setupCoroutines = false
    experimentalLatestCompilation = true

    // Check shrinking possibilities with `R8(full)` chain,
    // but don't replace the outgoing jar.
    replaceOutgoingJar = false
    shrink {
        fullMode = true
    }

    publicationConfig {
        developerId = "amal"
        developerName = "Artyom Shendrik"
        developerEmail = "artyom.shendrik@gmail.com"
    }

    apiValidation {
        nonPublicMarkers.add("fluxo.annotation.InternalFluxoApi")
    }
}

// Exclude Kotlin stdlib from the implementation classpath entirely
configurations.implementation {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
}

dependencies {
    // ALWAYS ADD SAME DEPS TO THE 'self' MODULE!

    implementation(libs.tomlj)
    // Spotless util classes are used internally
    implementation(libs.plugin.spotless)
    // Detekt ReportMergeTask is used internally
    implementation(libs.plugin.detekt)
    implementation(libs.detekt.core)
    // ASM for bytecode verification.
    implementation(libs.asm)

    implementation(platform(libs.okhttp.bom))

    compileOnly(libs.ktlint)

    compileOnly(libs.plugin.android)
    compileOnly(libs.plugin.intellij)
    compileOnly(libs.plugin.jetbrains.compose)
    compileOnly(libs.plugin.kotlin)
    compileOnly(libs.plugin.kotlin.compose)
    compileOnly(libs.plugin.ksp)

    compileOnly(libs.plugins.gradle.enterprise.toModuleDependency())

    testCompileOnly(libs.jetbrains.annotations)
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.proguard.plugin)
    testImplementation(libs.proguard.core)
    testImplementation(libs.r8)
    testImplementation(kotlin("test", libs.versions.kotlin.asProvider().get()))
}

tasks.test {
    useJUnitPlatform()
}

buildConfig {
    className("BuildConstants")
    packageName("fluxo.conf.data")
    buildConfigField("String", "PLUGIN_ID", "\"$pluginId\"")

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

    fun buildConfigField(
        fieldName: String,
        p: Provider<MinimalExternalModuleDependency>,
        compileOnly: Boolean = true,
    ) {
        p.get().apply {
            buildConfigField("String", fieldName, "\"$group:$name:$version\"")
        }
        if (compileOnly) {
            dependencies.compileOnly(p)
        }
    }
    buildConfigField("PROGUARD_PLUGIN", libs.proguard.plugin)
    buildConfigField("PROGUARD_CORE", libs.proguard.core)
    buildConfigField("KOTLINX_METADATA_JVM", libs.kotlin.metadata.jvm)
    buildConfigField("R8", libs.r8)
}
