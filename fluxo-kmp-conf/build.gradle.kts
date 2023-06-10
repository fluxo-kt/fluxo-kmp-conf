@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.binCompatValidator)
    alias(libs.plugins.deps.guard)
    alias(libs.plugins.detekt)
    alias(libs.plugins.gradle.plugin.publish)
}

val pluginName = "fluxo-kmp-conf"
val pluginId = "io.github.fluxo-kt.fluxo-kmp-conf"

group = "io.github.fluxo-kt"
version = libs.versions.fluxoKmpConf.get()

libs.versions.javaLangTarget.get().let { javaLangTarget ->
    logger.lifecycle("> Conf Java compatibility $javaLangTarget")
    java {
        JavaVersion.toVersion(javaLangTarget).let { v ->
            sourceCompatibility = v
            targetCompatibility = v
        }
    }

    val kotlinLangVersion = libs.versions.kotlinLangVersion.get()
    logger.lifecycle("> Conf Kotlin language and API $kotlinLangVersion")
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = javaLangTarget
            languageVersion = kotlinLangVersion
            apiVersion = kotlinLangVersion
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
        implementationClass = "GradleSetupPlugin"
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
