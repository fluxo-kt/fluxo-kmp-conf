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
}

val pluginName = "fluxo-kmp-conf"
val pluginId = "io.github.fluxo-kt.fluxo-kmp-conf"

group = "io.github.fluxo-kt"
version = libs.versions.fluxoKmpConf.get()

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
                    allWarningsAsErrors = true
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

        val test by getting(configureLatest)

        // Experimental test compilation with the latest Kotlin settings.
        // Don't try for sources with old compatibility settings.
        val isInCompositeBuild = gradle.includedBuilds.size > 1
        if (!isInCompositeBuild && kotlinVersion.toFloat() >= 1.6f) {
            create("experimentalTest") {
                configureLatest()
                @Suppress("DEPRECATION")
                source(main.defaultSourceSet)
                tasks.named("check") {
                    dependsOn(compileTaskProvider)
                }
            }.associateWith(test)
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

dependencies {
    compileOnly(libs.detekt.core)
    implementation(libs.plugin.detekt)
    detektPlugins(libs.detekt.formatting)

    compileOnly(libs.plugin.android)
    compileOnly(libs.plugin.binCompatValidator)
    compileOnly(libs.plugin.dokka)
    compileOnly(libs.plugin.intellij)
    compileOnly(libs.plugin.jetbrains.compose)
    compileOnly(libs.plugin.kotlin)
    compileOnly(libs.plugin.ksp)

    implementation(libs.ktlint)
    implementation(libs.plugin.spotless)
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

dependencyGuard {
    configuration("compileClasspath")
    configuration("runtimeClasspath")
}

detekt {
    parallel = true
    config.setFrom(rootProject.file("detekt.yml"))
    baseline = file("detekt-baseline.xml")
}
