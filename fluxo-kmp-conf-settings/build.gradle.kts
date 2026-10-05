plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.build.config)
    alias(libs.plugins.vanniktech.mvn.publish)
}

group = "io.github.fluxo-kt"
version = libs.versions.version.get()
description = "Settings part of fluxo-kmp-conf: puts the Gradle plugins fluxo applies by id " +
    "(KSP, plugin-publish, dependency analysis) on each project's build classpath."

val mainPluginId = libs.plugins.fluxo.conf.get().pluginId

fkcSetupGradlePlugin(
    pluginId = "$mainPluginId.settings",
    pluginName = "fluxo-kmp-conf-settings",
    pluginClass = "fluxo.settings.FluxoKmpConfSettingsPlugin",
    displayName = "Fluxo KMP Configuration (settings)",
    tags = listOf("kotlin", "kotlin-multiplatform", "gradle-configuration"),
    // Compiled into the main plugin too: its root-only fallback runs the same injector.
    kotlin = { sourceSets.main { kotlin.srcDir("src/shared/kotlin") } },
) {
    githubProject = "fluxo-kt/fluxo-kmp-conf"
    enablePublication = true
    setupVerification = true
    enableApiValidation = true
    setupCoroutines = false
    publicationConfig {
        developerId = "amal"
        developerName = "Art Shendrik"
        developerEmail = "artyom.shendrik@gmail.com"
    }
}

// The main plugin's compatibility suite resolves both plugins from one local repository.
plugins.withId("maven-publish") {
    extensions.configure<PublishingExtension>("publishing") {
        repositories.withType<MavenArtifactRepository>()
            .matching { it.name == "localDev" }
            .configureEach {
                url = uri(rootDir.resolve("fluxo-kmp-conf/build/compatibility/local-maven"))
            }
    }
}

buildConfig {
    className("BuildConstants")
    packageName("fluxo.settings.data")
    buildConfigField("String", "PLUGIN_VERSION", "\"$version\"")
    buildConfigField("String", "KSP_PLUGIN_VERSION", "\"${libs.versions.ksp.get()}\"")
    buildConfigField(
        "String",
        "GRADLE_PLUGIN_PUBLISH_PLUGIN_VERSION",
        "\"${libs.plugins.gradle.plugin.publish.get().version}\"",
    )
    buildConfigField(
        "String",
        "DEPS_ANALYSIS_PLUGIN_VERSION",
        "\"${libs.plugins.deps.analysis.get().version}\"",
    )
}
