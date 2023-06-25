import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaCompilation

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.sam.receiver)
    alias(libs.plugins.kotlinx.binCompatValidator)
    alias(libs.plugins.deps.guard)
    alias(libs.plugins.detekt)
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.build.config)
}

val pluginName = "fluxo-kmp-conf"
val pluginId = "io.github.fluxo-kt.fluxo-kmp-conf"
val experimentalTest = false

group = "io.github.fluxo-kt"
version = libs.versions.version.get()

samWithReceiver {
    annotation("org.gradle.api.HasImplicitReceiver")
}

kotlin {
    explicitApi()

    coreLibrariesVersion = libs.versions.kotlinCoreLibraries.get()

    target.compilations {
        val kotlinVersion = libs.versions.kotlinLangVersion.get()
        val main by getting {
            libs.versions.javaLangTarget.get().let { jvmVersion ->
                kotlinOptions {
                    jvmTarget = jvmVersion
                    languageVersion = kotlinVersion
                    apiVersion = kotlinVersion
//                    allWarningsAsErrors = true

                    freeCompilerArgs += "-Xcontext-receivers"
                    freeCompilerArgs += "-Xklib-enable-signature-clash-checks"
                    freeCompilerArgs += "-Xjsr305=strict"
                    freeCompilerArgs += "-Xjvm-default=all"
                    freeCompilerArgs += "-Xtype-enhancement-improvements-strict-mode"
                    freeCompilerArgs += "-Xvalidate-bytecode"
                    freeCompilerArgs += "-Xvalidate-ir"
                    freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
                    freeCompilerArgs += "-opt-in=fluxo.conf.dsl.InternalFluxoApi"
                }
                compileJavaTaskProvider.configure {
                    sourceCompatibility = jvmVersion
                    targetCompatibility = jvmVersion
                }
                logger.lifecycle("> Conf compatibility for Kotlin $kotlinVersion, JVM $jvmVersion")
            }
        }

        val configureLatest: (KotlinWithJavaCompilation<KotlinJvmOptions, *>).() -> Unit = {
            kotlinOptions {
                jvmTarget = "17"
                languageVersion = "2.0"
                apiVersion = "2.0"
            }
            compileJavaTaskProvider.configure {
                sourceCompatibility = "17"
                targetCompatibility = "17"
            }
        }

        getByName("test", configureLatest)

        // Experimental test compilation with the latest Kotlin settings.
        // Don't try for sources with old compatibility settings.
        val isInCompositeBuild = gradle.includedBuilds.size > 1
        if (!isInCompositeBuild && experimentalTest && kotlinVersion.toFloat() >= 1.4f) {
            create("experimentalTest") {
                configureLatest()
                // Deprecated in Kotlin 1.9.0
                @Suppress("DEPRECATION", "KotlinRedundantDiagnosticSuppress")
                source(main.defaultSourceSet)
                tasks.named("check") {
                    dependsOn(compileTaskProvider)
                }
            }
        }
    }
}

configurations.implementation {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-metadata-jvm")
    exclude(group = "org.ow2.asm")
}

fun Provider<PluginDependency>.toModuleDependency() = provider {
    val pd = get()
    val pluginId = pd.pluginId
    val mavenModule = "$pluginId:$pluginId.gradle.plugin"
    "$mavenModule:${pd.version}"
}

dependencies {
    detektPlugins(libs.detekt.formatting)

    // Spotless util classes are used internally
    implementation(libs.plugin.spotless)
    // Detekt ReportMergeTask is used internally
    implementation(libs.plugin.detekt)

    compileOnly(libs.detekt.core)
    compileOnly(libs.ktlint)

    compileOnly(libs.plugin.android)
    compileOnly(libs.plugin.binCompatValidator)
    compileOnly(libs.plugin.dokka)
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
        implementation: Boolean = false,
    ) {
        val aliasName = alias ?: name.lowercase().replace('_', '-')
        buildConfigField("String", "${name}_PLUGIN_ALIAS", "\"$aliasName\"")

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

    buildConfigField("DEPS_VERSIONS", libs.plugins.deps.versions)
    buildConfigField("DEPS_ANALYSIS", libs.plugins.deps.analysis)
    buildConfigField("DEPS_GUARD", libs.plugins.deps.guard, implementation = true)
    buildConfigField("TASK_TREE", libs.plugins.task.tree)
    buildConfigField("TASK_INFO", libs.plugins.task.info)
    buildConfigField("MODULE_DEPENDENCY_GRAPH", libs.plugins.module.dependency.graph)
    buildConfigField("BUILD_CONFIG", libs.plugins.build.config)
    buildConfigField("ABOUT_LIBRARIES", libs.plugins.about.libraries)

    // Automatically run task during Gradle sync in IDEA
    // TODO: Use maybeRegister to avoid unnecessary task creation?
    tasks.maybeCreate("prepareKotlinIdeaImport")
        .dependsOn("generateBuildConfig")
}

gradlePlugin {
    val projectUrl = "https://github.com/fluxo-kt/fluxo-kmp-conf"
    val scmUrl = "scm:git:git://github.com/fluxo-kt/fluxo-kmp-conf.git"

    website.set(projectUrl)
    vcsUrl.set("$projectUrl/tree/main")

    val shortDescr =
        "Convenience Gradle plugin for reliable configuration of Kotlin projects by Fluxo"
    plugins.create(pluginName) {
        id = pluginId
        implementationClass = "fluxo.conf.FluxoKmpConfPlugin"
        displayName = shortDescr
        description = "$shortDescr. See $projectUrl for more details."
        tags.set(
            listOf(
                "kotlin",
                "kotlin-multiplatform",
                "android",
                "gradle-configuration",
                "convenience",
            ),
        )
    }

    tasks.create("sourceJarTask", org.gradle.jvm.tasks.Jar::class.java) {
        from(pluginSourceSet.java.srcDirs)
        archiveClassifier.set("sources")
    }

    publishing {
        repositories {
            maven {
                name = "localDev"
                url = uri("../_/local-repo")
            }
        }

        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set("Fluxo BCV JS")
                description.set(shortDescr)
                url.set("$projectUrl/tree/main")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("amal")
                        name.set("Artyom Shendrik")
                        email.set("artyom.shendrik@gmail.com")
                    }
                }

                scm {
                    url.set(projectUrl)
                    connection.set(scmUrl)
                    developerConnection.set(scmUrl)
                }
            }
        }

        val signingKey = providers.environmentVariable("SIGNING_KEY").orNull?.replace("\\n", "\n")
        if (!signingKey.isNullOrEmpty()) {
            logger.lifecycle("> Conf SIGNING_KEY SET, applying signing configuration")
            project.plugins.apply("signing")
            extensions.configure<SigningExtension> {
                val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(publications)
            }
        } else {
            logger.warn("> Conf SIGNING_KEY IS NOT SET! Publications are unsigned")
        }
    }
}

apiValidation {
    nonPublicMarkers.add("fluxo.conf.dsl.InternalFluxoApi")
    nonPublicMarkers.add("kotlin.jvm.JvmSynthetic")
    // sealed classes constructors are not actually public
    ignoredClasses.add("kotlin.jvm.internal.DefaultConstructorMarker")
}

dependencyGuard {
    configuration("compileClasspath")
    configuration("runtimeClasspath")
}

detekt {
    parallel = true
    config.setFrom(rootProject.file("detekt.yml"))
    baseline = file("detekt-baseline.xml")
}
