@file:Suppress("TooManyFunctions")

import com.android.build.gradle.LibraryExtension
import fluxo.conf.dsl.FluxoPublicationConfig
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.create
import fluxo.conf.impl.get
import fluxo.conf.impl.hasExtension
import fluxo.conf.impl.named
import fluxo.conf.impl.the
import fluxo.conf.impl.withType
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType


/*
 * Set up publishing.
 *
 * Useful resources:
 *
 * - https://kotlinlang.org/docs/mpp-publish-lib.html
 * - https://central.sonatype.org/publish/publish-guide/
 * - https://central.sonatype.org/publish/publish-gradle/
 * - https://central.sonatype.org/publish/requirements/coordinates/#choose-your-coordinates
 * - https://getstream.io/blog/publishing-libraries-to-mavencentral-2021/
 * - https://dev.to/kotlin/how-to-build-and-publish-a-kotlin-multiplatform-library-going-public-4a8k
 */

// TODO: Set RC/alpha/beta releases status to Ivy's: milestone/integration when it will be possible
//  https://github.com/gradle/gradle/issues/12600
//  https://github.com/gradle/gradle/issues/20016

// TODO: Protect publication tasks from invalid GIT state (dirty, untracked files, etc.)
//  https://github.com/tbroyer/gradle-errorprone-plugin/blob/0f91e78/build.gradle.kts#L24

private const val USE_DOKKA: Boolean = true


public fun Project.setupPublication() {
    val config = requireDefaults<FluxoPublicationConfig>()
    when {
        hasExtension<KotlinMultiplatformExtension>() ->
            setupPublicationMultiplatform(config)

        hasExtension<LibraryExtension>() ->
            setupPublicationAndroidLibrary(config)

        hasExtension<GradlePluginDevelopmentExtension>() ->
            setupPublicationGradlePlugin(config)

        hasExtension<KotlinJvmProjectExtension>() ->
            setupPublicationKotlinJvm(config)

        hasExtension<JavaPluginExtension>() ->
            setupPublicationJava(config)

        else ->
            error("Unsupported project type for publication")
    }

    // Reproducible builds setup
    // https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives
    tasks.withType<AbstractArchiveTask> {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

private fun Project.setupPublicationMultiplatform(config: FluxoPublicationConfig) {
    applyMavenPublishPlugin()

    group = config.group
    version = config.version

    setupPublicationExtension(config)
    setupPublicationRepository(config)

    if (!isGenericCompilationEnabled) return
    multiplatformExtension.apply {
        if (targets.any { it.platformType == KotlinPlatformType.androidJvm }) {
            try {
                // Kotlin before 1.9
                @Suppress("DEPRECATION", "KotlinRedundantDiagnosticSuppress")
                android().publishLibraryVariants("release", "debug")
            } catch (e: Throwable) {
                logger.error("android.publishLibraryVariants error: $e", e)
            }

            // Gradle 8 compatibility
            if (config.isSigningEnabled) {
                val deps = tasks.matching {
                    it.name.startsWith("sign") && it.name.endsWith("Publication")
                }
                tasks.matching {
                    it.name.endsWith("PublicationToMavenLocal") ||
                        it.name.endsWith("PublicationToMavenRepository")
                }.configureEach {
                    dependsOn(deps)
                }
            }
        }
    }
}

private fun Project.setupPublicationAndroidLibrary(config: FluxoPublicationConfig) {
    if (!isGenericCompilationEnabled) {
        return
    }

    applyMavenPublishPlugin()

    val androidExtension = the<LibraryExtension>()

    val sourceJarTask = tasks.create<Jar>("sourceJarTask") {
        from(androidExtension.sourceSets.getByName("main").java.srcDirs)
        archiveClassifier.set("source")
    }

    fun PublicationContainer.createMavenPublication(name: String, artifactIdSuffix: String) {
        create<MavenPublication>(name) {
            from(components[name])
            artifact(sourceJarTask)

            groupId = config.group
            version = config.version
            artifactId = "${project.name}$artifactIdSuffix"

            setupPublicationPom(project, config)
        }
    }

    afterEvaluate {
        configureExtension<PublishingExtension> {
            publications {
                createMavenPublication(name = "debug", artifactIdSuffix = "-debug")
                createMavenPublication(name = "release", artifactIdSuffix = "")
            }
        }
    }

    setupPublicationRepository(config)
}

private fun Project.setupPublicationGradlePlugin(config: FluxoPublicationConfig) {
    applyMavenPublishPlugin()

    group = config.group
    version = config.version

    val gradlePluginExtension = the<GradlePluginDevelopmentExtension>()

    val sourceJarTask = tasks.create<Jar>("sourceJarTask") {
        from(gradlePluginExtension.pluginSourceSet.java.srcDirs)
        archiveClassifier.set("sources")
    }

    afterEvaluate {
        setupPublicationExtension(config, sourceJarTask)
    }

    setupPublicationRepository(config)
}

private fun Project.setupPublicationKotlinJvm(config: FluxoPublicationConfig) {
    if (!isGenericCompilationEnabled) {
        return
    }

    applyMavenPublishPlugin()

    group = config.group
    version = config.version

    val kotlinPluginExtension = the<KotlinJvmProjectExtension>()

    val sourceJarTask = tasks.create<Jar>("sourceJarTask") {
        from(kotlinPluginExtension.sourceSets.getByName("main").kotlin.srcDirs)
        archiveClassifier.set("sources")
    }

    setupPublicationExtension(config, sourceJarTask)
    setupPublicationRepository(config)
}

private fun Project.setupPublicationJava(config: FluxoPublicationConfig) {
    if (!isGenericCompilationEnabled) {
        return
    }

    applyMavenPublishPlugin()

    group = config.group
    version = config.version

    val javaPluginExtension = the<JavaPluginExtension>()

    val sourceJarTask = tasks.create<Jar>("sourceJarTask") {
        from(javaPluginExtension.sourceSets.getByName("main").java.srcDirs)
        archiveClassifier.set("sources")
    }

    setupPublicationExtension(config, sourceJarTask)
    setupPublicationRepository(config)
}

internal fun MavenPublication.setupPublicationPom(
    project: Project,
    config: FluxoPublicationConfig,
) {
    // Publish docs with each artifact.
    val useDokka = USE_DOKKA && !config.isSnapshot
    try {
        check(useDokka) { "Dokka disabled" }

        val taskName = "dokkaHtmlAsJavadoc"
        val dokkaHtmlAsJavadoc = project.tasks.run {
            findByName(taskName) ?: run {
                val dokkaHtml = project.tasks.named<DokkaTask>("dokkaHtml")
                create<Jar>(taskName) {
                    setupJavadocJar()
                    from(dokkaHtml)
                }
            }
        }
        artifact(dokkaHtmlAsJavadoc)
    } catch (e: Throwable) {
        if (useDokka) {
            project.logger.warn("Fallback to Javadoc. Dokka publication setup error: $e", e)
        }
        artifact(project.javadocJarTask())
    }

    pom {
        name.set(config.projectName)
        description.set(config.projectDescription)
        url.set(config.publicationUrl)

        if (!config.licenseName.isNullOrEmpty()) {
            licenses {
                license {
                    name.set(config.licenseName)
                    url.set(config.licenseUrl)
                }
            }
        }

        developers {
            if (!config.developerId.isNullOrEmpty() ||
                !config.developerName.isNullOrEmpty() ||
                !config.developerEmail.isNullOrEmpty()
            ) {
                developer {
                    config.developerId?.let { id.set(it) }
                    config.developerName?.let { name.set(it) }
                    config.developerEmail?.let { email.set(it) }
                }
            }
        }

        scm {
            url.set(config.projectUrl)
            connection.set(config.scmUrl)
            developerConnection.set(config.scmUrl)
            config.scmTag.takeIf { !it.isNullOrEmpty() }?.let {
                tag.set(it)
            }
        }
    }
}

private val signingKeyNotificationLogged = AtomicBoolean()

internal fun Project.setupPublicationRepository(config: FluxoPublicationConfig) {
    val notify = signingKeyNotificationLogged.compareAndSet(false, true)
    if (config.isSigningEnabled) {
        if (notify) logger.lifecycle("> Conf SIGNING_KEY SET, applying signing configuration")
        plugins.apply("signing")
    } else if (notify) {
        logger.warn("> Conf SIGNING_KEY IS NOT SET! Publications are unsigned")
    }

    configureExtension<PublishingExtension> {
        if (config.isSigningEnabled) {
            configureExtension<SigningExtension> {
                useInMemoryPgpKeys(config.signingKey, config.signingPassword)
                sign(publications)
            }
        }

        repositories {
            maven {
                setUrl(config.repositoryUrl)

                credentials {
                    username = config.repositoryUserName
                    password = config.repositoryPassword
                }
            }
        }
    }
}

private fun Project.setupPublicationExtension(
    config: FluxoPublicationConfig,
    sourceJarTask: Jar? = null,
) {
    configureExtension<PublishingExtension> {
        publications.withType<MavenPublication> {
            sourceJarTask?.let { artifact(it) }
            setupPublicationPom(project, config)
        }
    }
}

private fun Project.applyMavenPublishPlugin() = plugins.apply("maven-publish")

private fun Project.javadocJarTask(): Task {
    val taskName = "javadocJar"
    return tasks.findByName(taskName) ?: tasks.create<Jar>(taskName, Jar::setupJavadocJar)
}

private fun Jar.setupJavadocJar() {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Assembles a jar archive containing the Javadoc API documentation."
    archiveClassifier.set("javadoc")
}
